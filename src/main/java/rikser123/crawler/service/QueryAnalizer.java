package rikser123.crawler.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.SearchResponseDtoWithContent;
import rikser123.crawler.dto.UserQueryDto;


import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueryAnalizer {
  private final BlockingQueue<UserQueryDto> queue = new LinkedBlockingQueue<>();
  private final ExecutorService executors = Executors.newVirtualThreadPerTaskExecutor();

  private Semaphore queueSemaphore;
  private final FetchConfigProperties fetchConfigProperties;
  private final BothubService bothubService;

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

  public void initAnalysis(UserQueryDto request) {
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
      log.info("summ {}", summaries);

      var response = bothubService.getQueryAnalysis(userQuery, summaries);
      // TODO ответ в оркестратор с успехом всего квери
    } catch (IllegalStateException exception) {
      // TODO ответ в очередь с целым квери с ошибкой сообщение в оркестратор
    }
    catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    } finally {
      queueSemaphore.release();
    }
  }
}
