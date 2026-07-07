package rikser123.crawler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import rikser123.crawler.dto.SearchResponseDto;
import rikser123.crawler.dto.MessageUserQueryDto;
import rikser123.crawler.dto.SearchResponseDtoStatus;
import rikser123.crawler.dto.SearchResponseDtoWithContent;
import rikser123.crawler.dto.UserQueryAnalysisDto;
import rikser123.crawler.dto.UserQueryDto;
import rikser123.crawler.dto.event.FinishAnalysisEvent;
import rikser123.crawler.dto.event.FinishCleanContentEvent;
import rikser123.crawler.dto.event.FinishDownloadContentEvent;
import rikser123.crawler.dto.event.FinishSplitChunksEvent;
import rikser123.crawler.dto.event.ResponseProcessingErrorEvent;
import rikser123.crawler.dto.event.SummaryEvent;
import rikser123.crawler.mapper.UserQueryMapper;
import rikser123.crawler.repository.entity.SearchQueryOutboxMessage;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
  private final QueryAnalizer queryAnalizer;
  private final SearchResponseMessageService searchResponseMessageService;
  private final UserQueryMapper userQueryMapper;
  private final SearchQueryMessageService searchQueryMessageService;

  public void initResponseProcessing(MessageUserQueryDto messageDto) {
   var userQueryDto = userQueryMapper.mapMessageToDto(messageDto);

    userQueryInProcessing.put(userQueryDto.getSearchQueryId(), userQueryDto);
    var responses = userQueryDto.getSearchResponses()
      .stream()
      .map(SearchResponseDtoWithContent::getSearchResponse)
      .toList();

    responses.forEach(response -> {
      var isResponseInProcessing = responsesQueryInProcessing
        .values()
        .stream()
        .anyMatch(responseQuery -> responseQuery.getUrl().equals(response.getUrl()));

      if (!isResponseInProcessing) {
        crawler.initProcessing(response);
      }
    });
  }

  @EventListener
  void finishDownloadContentListener(FinishDownloadContentEvent event) {
    textExtractor.initProcessing(event.getContext());
  }

  @EventListener
  void finisCleanContentListener(FinishCleanContentEvent event) {
    chunkSplitter.initProcessing(event.getSearchResponseDto());
  }

  @EventListener
  void finishSplitChunkEventListener(FinishSplitChunksEvent event) {
    summariser.initProcessing(event.getDtoWithChunks());
  }

  @EventListener
  void summaryEventListener(SummaryEvent summaryEvent) {
    var dto = summaryEvent.getSearchDto();
    var responses = getAllResponsesWithUrl(dto.getSearchResponse().getUrl());
    setResponseQueryStatus(responses, SearchResponseDtoStatus.PROCESSED);
    responses.forEach(response -> {
      response.setContent(dto.getContent());
    });

    var processedUserQuery = userQueryInProcessing
      .values()
      .stream()
      .filter(userQuery ->
        userQuery.getSearchResponses()
          .stream()
          .allMatch(response -> {
            var status = response.getStatus();
            return status == SearchResponseDtoStatus.PROCESSED || status == SearchResponseDtoStatus.ERROR;
          }
      )).peek(query -> {
        userQueryInProcessing.remove(query.getSearchQueryId());
      }).toList();
    processedUserQuery.forEach(query -> {
      queryAnalizer.initProcessing(query);
    });
  }

  @EventListener
  void finishAnalysisEventListener(FinishAnalysisEvent event) {
    var dto = event.getDto();
    userQueryInProcessing.remove(dto.getSearchQueryId());
    SearchQueryOutboxMessage message;

    if (!Objects.isNull(dto.getError())) {
      message = searchQueryMessageService.createQueryOutboxErrorMessage(dto, dto.getError().getMessage());
    } else {
      message = searchQueryMessageService.createQueryOutboxSuccessMessage(dto);
    }
    searchQueryMessageService.save(message);
  }

  @EventListener
  void processingErrorListener(ResponseProcessingErrorEvent event) {
    var errorMessage = event.getMessage();
    var url = event.getUrl();

    var responses = getAllResponsesWithUrl(url);
    setResponseQueryStatus(responses, SearchResponseDtoStatus.ERROR);
    var outboxMessages = responses.stream().map(response ->
      searchResponseMessageService.createOutboxRequestError(response.getSearchResponse().getSearchResponseId(), errorMessage)
    ).toList();
    searchResponseMessageService.saveAll(outboxMessages);

    var failedQueries = userQueryInProcessing
      .values()
      .stream()
      .filter(query ->
        query.getSearchResponses().stream().allMatch(response -> response.getStatus() == SearchResponseDtoStatus.ERROR)
      ).toList();

    failedQueries.forEach(query -> {
      userQueryInProcessing.remove(query.getSearchQueryId());
      var analysisDto = new UserQueryAnalysisDto();
      analysisDto.setUserId(query.getUserId());
      analysisDto.setSearchQueryId(query.getSearchQueryId());

      searchQueryMessageService.createQueryOutboxErrorMessage(analysisDto, "Обработка всех ответов от яндекса завершился ошибкой!");
    });

  }

  private List<SearchResponseDtoWithContent> getAllResponsesWithUrl(String url) {
    return userQueryInProcessing.values()
      .stream()
      .map(UserQueryDto::getSearchResponses)
      .flatMap(Collection::stream)
      .filter(responseQuery -> responseQuery.getSearchResponse().getUrl().equals(url))
      .toList();
  }

  private void setResponseQueryStatus(List<SearchResponseDtoWithContent> queries, SearchResponseDtoStatus status) {
    queries.forEach(query -> {
      responsesQueryInProcessing.remove(query.getSearchResponse().getSearchResponseId());
      query.setStatus(status);
    });
  }
}
