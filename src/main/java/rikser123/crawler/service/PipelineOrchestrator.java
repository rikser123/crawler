package rikser123.crawler.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import rikser123.crawler.dto.queryResponse.QueryResponseDto;
import rikser123.crawler.dto.userQuery.MessageUserQueryDto;
import rikser123.crawler.dto.queryResponse.QueryResponseDtoStatus;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;
import rikser123.crawler.dto.userQuery.UserQueryAnalysisDto;
import rikser123.crawler.dto.userQuery.UserQueryDto;
import rikser123.crawler.dto.event.FinishAnalysisEvent;
import rikser123.crawler.dto.event.FinishCleanContentEvent;
import rikser123.crawler.dto.event.FinishDownloadContentEvent;
import rikser123.crawler.dto.event.FinishSplitChunksEvent;
import rikser123.crawler.dto.event.ResponseProcessingErrorEvent;
import rikser123.crawler.dto.event.SummaryEvent;
import rikser123.crawler.dto.userQuery.UserQueryInitialTimeDto;
import rikser123.crawler.mapper.UserQueryMapper;
import rikser123.crawler.repository.entity.SearchQueryOutboxMessage;
import rikser123.crawler.repository.entity.SearchResponseOutboxMessage;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PipelineOrchestrator {
  private final Map<UUID, UserQueryInitialTimeDto> userQueryInProcessing = new ConcurrentHashMap<>();
  private final Map<UUID, QueryResponseDto> responsesQueryInProcessing = new ConcurrentHashMap<>();
  private final Object pipelineLock = new Object();
  private final ScheduledExecutorService cleanUpExecutor = Executors.newSingleThreadScheduledExecutor();

  private final Crawler crawler;
  private final TextExtractor textExtractor;
  private final ChunkSplitter chunkSplitter;
  private final Summariser summariser;
  private final QueryAnalizer queryAnalizer;
  private final SearchResponseMessageService searchResponseMessageService;
  private final UserQueryMapper userQueryMapper;
  private final SearchQueryMessageService searchQueryMessageService;

  @Value("${fetch.clear-delay}")
  private int clearDelay;

  @PostConstruct
  void init() {
    cleanUpExecutor.scheduleAtFixedRate(this::cleanUp, 0, clearDelay, TimeUnit.SECONDS);
  }

  public void initResponseProcessing(MessageUserQueryDto messageDto) {
   var userQueryDto = userQueryMapper.mapMessageToDto(messageDto);
   log.info("PIPELINE: crawling {}", messageDto);

    var queryTimeDto = new UserQueryInitialTimeDto();
    queryTimeDto.setStartTime(Instant.now());
    queryTimeDto.setDto(userQueryDto);
    userQueryInProcessing.put(userQueryDto.getSearchQueryId(), queryTimeDto);
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
        responsesQueryInProcessing.put(response.getSearchResponseId(), response);
        crawler.initProcessing(response);
      }
    });
  }

  @EventListener
  void finishDownloadContentListener(FinishDownloadContentEvent event) {
    log.info("PIPELINE: FinishDownloadContentEvent {}", event.getDto().getSearchResponse().getSearchResponseId());
    textExtractor.initProcessing(event.getDto());
  }

  @EventListener
  void finisCleanContentListener(FinishCleanContentEvent event) {
    log.info("PIPELINE: FinishCleanContentEvent {}", event.getDto().getSearchResponse().getSearchResponseId());
    chunkSplitter.initProcessing(event.getDto());
  }

  @EventListener
  void finishSplitChunkEventListener(FinishSplitChunksEvent event) {
    log.info("PIPELINE: FinishSplitChunksEvent {}", event.getDto().getSearchResponse().getSearchResponseId());
    summariser.initProcessing(event.getDto());
  }

  @EventListener
  void summaryEventListener(SummaryEvent summaryEvent) {
    log.info("PIPELINE: SummaryEvent {}", summaryEvent.getDto().getSearchResponse().getSearchResponseId());
    var dto = summaryEvent.getDto();
    var responses = getAllResponsesWithUrl(dto.getSearchResponse().getUrl());
    setResponseQueryStatus(responses, QueryResponseDtoStatus.PROCESSED);
    responses.forEach(response -> {
      response.setContent(dto.getContent());
    });

    synchronized (pipelineLock) {
      analyzeProcessedQueries();
    }
  }

  @EventListener
  void finishAnalysisEventListener(FinishAnalysisEvent event) {
    log.info("PIPELINE: FinishAnalysisEvent {}", event.getDto().getSearchQueryId());
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
    synchronized (pipelineLock) {
      log.info("PIPELINE: ResponseProcessingErrorEvent {}", event.getSearchResponseId());
      var errorMessage = event.getMessage();
      var url = event.getUrl();

      var responses = getAllResponsesWithUrl(url);
      setResponseQueryStatus(responses, QueryResponseDtoStatus.ERROR);
      saveOutboxResponseMessages(responses, errorMessage);

      var failedQueries = userQueryInProcessing
        .values()
        .stream()
        .filter(query ->
          query.
            getDto()
            .getSearchResponses()
            .stream()
            .allMatch(response -> response.getStatus() == QueryResponseDtoStatus.ERROR)
        ).toList();

      handleFailedQueries(failedQueries, "Обработка всех ответов от яндекса завершился ошибкой!");

      analyzeProcessedQueries();
    }
  }

  private List<SearchResponseDtoWithContent> getAllResponsesWithUrl(String url) {
    return userQueryInProcessing.values()
      .stream()
      .map(UserQueryInitialTimeDto::getDto)
      .map(UserQueryDto::getSearchResponses)
      .flatMap(Collection::stream)
      .filter(responseQuery -> responseQuery.getSearchResponse().getUrl().equals(url))
      .toList();
  }

  private void setResponseQueryStatus(List<SearchResponseDtoWithContent> queries, QueryResponseDtoStatus status) {
    queries.forEach(query -> {
      responsesQueryInProcessing.remove(query.getSearchResponse().getSearchResponseId());
      query.setStatus(status);
    });
  }

  private void analyzeProcessedQueries() {
    var processedUserQuery = userQueryInProcessing
      .values()
      .stream()
      .filter(userQuery ->
        userQuery
          .getDto()
          .getSearchResponses()
          .stream()
          .allMatch(response -> {
              var status = response.getStatus();
              return status == QueryResponseDtoStatus.PROCESSED || status == QueryResponseDtoStatus.ERROR;
            }
          )).peek(query -> {
        userQueryInProcessing.remove(query.getDto().getSearchQueryId());
      }).toList();

    processedUserQuery.forEach(query -> {
      queryAnalizer.initProcessing(query.getDto());
    });
  }

  private void cleanUp() {
    var outdatedQueries = userQueryInProcessing.values()
      .stream()
      .filter(timeQuery -> {
        var currentTime = Instant.now();
        var queryStartTime = timeQuery.getStartTime();
        var duration = Duration.between(queryStartTime, currentTime);
        return duration.toSeconds() > clearDelay;
    })
    .toList();

    var outdatedResponses = outdatedQueries
        .stream()
        .map(UserQueryInitialTimeDto::getDto)
        .map(UserQueryDto::getSearchResponses)
        .flatMap(Collection::stream)
        .toList();

    outdatedResponses.forEach(response -> {
      responsesQueryInProcessing.remove(response.getSearchResponse().getSearchResponseId());
    });

    final String errorMessage = String.format(
      "Не удлалось обработать запрос позьзователя - прошло более %s минут",
      clearDelay
    );
    saveOutboxResponseMessages(outdatedResponses, errorMessage);
    handleFailedQueries(outdatedQueries, errorMessage);
  }

  private List<SearchResponseOutboxMessage> saveOutboxResponseMessages(
    List<SearchResponseDtoWithContent> responses, String errorMessage
  ) {
    var outboxMessages = responses.stream()
      .map(response ->
      searchResponseMessageService.createOutboxRequestError(
        response.getSearchResponse().getSearchResponseId(),
        errorMessage
      )
    ).toList();
    return searchResponseMessageService.saveAll(outboxMessages);
  }

  private void handleFailedQueries(List<UserQueryInitialTimeDto> failedQueries, String errorMessage) {
    failedQueries.forEach(queryTime -> {
      var query = queryTime.getDto();
      userQueryInProcessing.remove(query.getSearchQueryId());
      var analysisDto = new UserQueryAnalysisDto();
      analysisDto.setUserId(query.getUserId());
      analysisDto.setSearchQueryId(query.getSearchQueryId());
      searchQueryMessageService.createQueryOutboxErrorMessage(
        analysisDto,
        errorMessage
      );
    });
  }
}
