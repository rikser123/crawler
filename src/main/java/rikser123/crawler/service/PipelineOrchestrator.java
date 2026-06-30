package rikser123.crawler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import rikser123.crawler.dto.SearchResponseDto;
import rikser123.crawler.dto.MessageUserQueryDto;
import rikser123.crawler.dto.SearchResponseDtoStatus;
import rikser123.crawler.dto.UserQueryDto;
import rikser123.crawler.dto.event.FinishCleanContentEvent;
import rikser123.crawler.dto.event.FinishDownloadContentEvent;
import rikser123.crawler.dto.event.FinishSplitChunksEvent;
import rikser123.crawler.dto.event.ResponseProcessingErrorEvent;
import rikser123.crawler.mapper.SearchResponseMapper;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineOrchestrator {
  private static final Map<UUID, UserQueryDto> userQueryInProcessing = new ConcurrentHashMap<>();
  private static final Map<UUID, SearchResponseDto> responsesQueryInProcessing = new ConcurrentHashMap<>();

  private final Crawler crawler;
  private final TextExtractor textExtractor;
  private final ChunkSplitter chunkSplitter;
  private final SearchResponseMessageService searchResponseMessageService;
  private final SearchResponseMapper searchResponseMapper;

  public void initResponseProcessing(MessageUserQueryDto queryDto) {
    var responses = queryDto.getSearchResponses()
      .stream()
      .map(searchResponseMapper::mapToDtoFromMessage)
      .toList();

    responses.forEach(response -> {
      response.setQueryId(queryDto.getSearchQueryId());
    });

    var userQueryDto = new UserQueryDto();
    userQueryDto.setSearchResponses(responses);
    userQueryDto.setSearchQueryId(queryDto.getSearchQueryId());
    userQueryDto.setUserId(queryDto.getUserId());

    userQueryInProcessing.put(queryDto.getSearchQueryId(), userQueryDto);

    var activeResponses = responses
      .stream()
      .filter(response ->
        responsesQueryInProcessing
          .values()
          .stream()
          .anyMatch(responseQuery -> responseQuery.getUrl().equals(response.getUrl())))
      .toList();

    if (!activeResponses.isEmpty()) {
      activeResponses.forEach(response -> {
        crawler.initDownloading(response);
      });
    }
  }

  @EventListener
  void finishDownloadContentListener(FinishDownloadContentEvent event) {
    textExtractor.initExtraction(event.getContext());
  }

  @EventListener
  void finisCleanContentListener(FinishCleanContentEvent event) {
    chunkSplitter.initSpliting(event.getSearchResponseDto());
  }

  @EventListener
  void finishSplitChunkEventListener(FinishSplitChunksEvent event) {

  }

  @EventListener
  void processingErrorListener(ResponseProcessingErrorEvent event) {
    var errorMessage = event.getMessage();
    var url = event.getUrl();
    var sameProcessingResponse = responsesQueryInProcessing
      .values()
      .stream()
      .filter(response -> response.getUrl().equals(url))
      .peek(response -> {
        var responseId = response.getSearchResponseId();
        var queryId = response.getQueryId();
        responsesQueryInProcessing.remove(responseId);

       Optional.ofNullable(userQueryInProcessing.get(queryId))
         .map(UserQueryDto::getSearchResponses)
         .orElse(Collections.emptyList())
        .stream()
        .filter(res -> response.getSearchResponseId().equals(responseId))
        .findFirst()
        .ifPresent(resp -> resp.setStatus(SearchResponseDtoStatus.ERROR));
      }).map(resp ->
        searchResponseMessageService.createOutboxRequestError(resp.getSearchResponseId(), errorMessage))
      .toList();

    searchResponseMessageService.saveAll(sameProcessingResponse);
  }
}
