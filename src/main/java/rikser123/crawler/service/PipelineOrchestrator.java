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
import rikser123.crawler.dto.event.SummaryEvent;
import rikser123.crawler.mapper.SearchResponseMapper;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineOrchestrator {
  private final Map<UUID, UserQueryDto> userQueryInProcessing = new ConcurrentHashMap<>();
  private final Map<UUID, SearchResponseDto> responsesQueryInProcessing = new ConcurrentHashMap<>();

  private final Crawler crawler;
  private final TextExtractor textExtractor;
  private final ChunkSplitter chunkSplitter;
  private final Summariser summariser;
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

    responses.forEach(response -> {
      var isResponseInProcessing = responsesQueryInProcessing
        .values()
        .stream()
        .anyMatch(responseQuery -> responseQuery.getUrl().equals(response.getUrl()));

      if (!isResponseInProcessing) {
        crawler.initDownloading(response);
      }
    });
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
    summariser.initSummarising(event.getDtoWithChunks());
  }

  @EventListener
  void summaryEventListener(SummaryEvent summaryEvent) {
    var dto = summaryEvent.getSearchDto();
    setResponseQueryStatus(dto.getSearchResponse(), SearchResponseDtoStatus.PROCESSED);

    var processedUserQuery = userQueryInProcessing
      .values()
      .stream()
      .filter(userQuery ->
        userQuery.getSearchResponses()
          .stream()
          .allMatch(response -> response.getStatus() == SearchResponseDtoStatus.PROCESSED)
      ).peek(userQuery -> {
        userQueryInProcessing.remove(userQuery.getSearchQueryId());
      }).toList();
    // TODO отправлямем в дипсик
  }

  @EventListener
  void processingErrorListener(ResponseProcessingErrorEvent event) {
    var errorMessage = event.getMessage();
    var url = event.getUrl();
    // TODO общее сообщение об ошибочном статусе запроса, если все ответы ошибочны
    var sameProcessingResponse = responsesQueryInProcessing
      .values()
      .stream()
      .filter(response -> response.getUrl().equals(url))
      .peek(response -> {
        setResponseQueryStatus(response, SearchResponseDtoStatus.ERROR);
      }).map(resp ->
        searchResponseMessageService.createOutboxRequestError(resp.getSearchResponseId(), errorMessage))
      .toList();

    searchResponseMessageService.saveAll(sameProcessingResponse);
  }

  private void setResponseQueryStatus(SearchResponseDto response, SearchResponseDtoStatus status) {
    var responseId = response.getSearchResponseId();
    responsesQueryInProcessing.remove(responseId);

    userQueryInProcessing.values()
      .stream()
     .map(UserQueryDto::getSearchResponses)
     .flatMap(Collection::stream)
     .filter(responseQuery -> responseQuery.getUrl().equals(response.getUrl()))
     .forEach(responseQuery -> response.setStatus(status));
  }
}
