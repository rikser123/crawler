package rikser123.crawler.service;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import rikser123.crawler.component.EventPublisher;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.MessageError;
import rikser123.crawler.dto.SearchResponseDtoWithContent;
import rikser123.crawler.dto.UserQueryAnalysisDto;
import rikser123.crawler.dto.UserQueryDto;
import rikser123.crawler.dto.event.FinishAnalysisEvent;


import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryAnalizer implements PipelineStep<UserQueryDto> {
  private final BlockingQueue<UserQueryDto> queue = new LinkedBlockingQueue<>();
  private final ExecutorService executors = Executors.newVirtualThreadPerTaskExecutor();

  private Semaphore queueSemaphore;
  private final FetchConfigProperties fetchConfigProperties;
  private final BothubService bothubService;
  private final EventPublisher eventPublisher;

  @PostConstruct
  void init() {
    queueSemaphore = new Semaphore(fetchConfigProperties.getQueueLimit());

    executors.execute(() -> {
      while (true) {
        try {
          var request = queue.take();
          executors.execute(() -> makeAnalysis(request));
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    });
  }

  @PreDestroy
  void shutdown() {
    log.info("Shutting down QueryAnalizer...");
    executors.shutdown();
    try {
      if (!executors.awaitTermination(30, TimeUnit.SECONDS)) {
        executors.shutdownNow();
      }
    } catch (InterruptedException exception) {
      executors.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void initProcessing(UserQueryDto request) {
    queue.add(request);
  }

  private void makeAnalysis(UserQueryDto request) {
    try {
      queueSemaphore.acquire();
      var userQuery = request.getQueryText();
      var summaries = request.getSearchResponses()
        .stream()
        .map(SearchResponseDtoWithContent::getContent)
        .toList();
      var response = bothubService.getQueryAnalysis(userQuery, summaries);
      var event = createEvent(request, response, null);
      eventPublisher.publishEvent(event);
    } catch (IllegalStateException exception) {
      log.warn("Не удалось получить анализ данных от модели", exception);
      var event = createEvent(request, null, "Не удалось получить анализ данных от модели");
      eventPublisher.publishEvent(event);
    }
    catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    } finally {
      queueSemaphore.release();
    }
  }

  private FinishAnalysisEvent createEvent(UserQueryDto request, @Nullable String analysis, @Nullable String errorMessage) {
    var event = new FinishAnalysisEvent();
    var dto = new UserQueryAnalysisDto();
    dto.setUserId(request.getUserId());
    dto.setSearchQueryId(request.getSearchQueryId());
    dto.setAnalysis(analysis);

    if (StringUtils.isNotEmpty(errorMessage)) {
      var error = new MessageError();
      error.setMessage(errorMessage);
      dto.setError(error);
    }
    event.setDto(dto);
    return event;
  }
}
