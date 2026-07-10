package rikser123.crawler.service;

import crawlercommons.robots.SimpleRobotRulesParser;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import rikser123.bundle.service.RedisCacheService;
import rikser123.crawler.component.CrawlerResponseExtractor;
import rikser123.crawler.component.EventPublisher;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.queryResponse.DelayedQueryResponseDtoCrawler;
import rikser123.crawler.dto.queryResponse.QueryResponseDto;
import rikser123.crawler.dto.queryResponse.QueryResponseDtoCrawler;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;
import rikser123.crawler.dto.event.FinishDownloadContentEvent;
import rikser123.crawler.exception.BigSizeContentException;
import rikser123.crawler.utils.CaptchaUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
@Service
@Slf4j
public class Crawler implements PipelineStep<QueryResponseDto> {
  private static final Random random = new Random();
  private static final Integer RANDOM_BOUND = 30;

  private final ExecutorService executors = Executors.newVirtualThreadPerTaskExecutor();
  private final BlockingQueue<QueryResponseDtoCrawler> queue = new LinkedBlockingQueue<>();
  private final DelayQueue<DelayedQueryResponseDtoCrawler> delayQueue = new DelayQueue<>();
  private Semaphore queueSemaphore;
  private Semaphore delayQueueSemaphore;

  private final FetchConfigProperties fetchProperties;
  private final CrawlerResponseExtractor crawlerResponseExtractor;
  private final RestTemplate restTemplate;
  private final RedisCacheService redisCacheService;
  private final EventPublisher eventPublisher;

  @PostConstruct
  void init() {
    queueSemaphore = new Semaphore(fetchProperties.getQueueLimit());
    delayQueueSemaphore = new Semaphore(fetchProperties.getTimeoutQueueLimit());

    initThreadPool(queue, queueSemaphore, "MAIN");
    initThreadPool(delayQueue, delayQueueSemaphore, "DELAY");
  }

  @PreDestroy
  void shutdown() {
    executors.shutdown();
    try {
      if (!executors.awaitTermination(30, TimeUnit.SECONDS)) {
        executors.shutdownNow();
      }
    } catch (InterruptedException e) {
      executors.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void initProcessing(QueryResponseDto resultDto) {
    var requestDto = new QueryResponseDtoCrawler();
    requestDto.setAttempt(0);
    requestDto.setSearchResponse(resultDto);

    var sameDomainCount = queue.stream()
      .filter(response ->
        response.getSearchResponse().getDomain().equals(resultDto.getDomain()))
      .count();

    if (sameDomainCount > 0) {
      addDelayProcess(requestDto);
    } else {
      queue.add(requestDto);
    }
  }

  private <T extends QueryResponseDtoCrawler> void initThreadPool(BlockingQueue<T> queue, Semaphore semaphore, String queueType) {
    executors.execute(() -> {
      while (true) {
        try {
          var request = queue.take();

          executors.execute(() -> {
            try {
              var content = downloadLinkContent(request, semaphore);
              if (!Objects.isNull(content)) {
                var requestWithContent = prepareRequestsWithContent(request, content);
                publishFinishDownloadContentEvent(requestWithContent);
              }
            } catch (IllegalStateException e) {
              log.error("IllegalStateException in download thread: {}, url={}", e.getMessage(), request.getSearchResponse().getUrl(), e);
              eventPublisher.publishResponseProcessingErrorEvent(request.getSearchResponse(), e.getMessage());
            }
          });
        } catch (InterruptedException e) {
          log.warn("Thread pool consumer for queue type {} was interrupted", queueType);
          Thread.currentThread().interrupt();
          break;
        }
      }
    });
  }

  private <T extends QueryResponseDtoCrawler> String downloadLinkContent(
    T requestDto,
    Semaphore semaphore
    ) {
    var link = requestDto.getSearchResponse().getUrl();
    var currentAttempt = requestDto.getAttempt();

    if (currentAttempt >= fetchProperties.getMaxDownloadAttempt()) {
      log.warn("Превышен лимит попыток скачивания {}, maxAttempt={}", link, fetchProperties.getMaxDownloadAttempt());
      throw new IllegalStateException("Превышен лимит попыток скачивания!");
    }

    var isAllowed = isParsingAllowed(link);

    if (!isAllowed) {
      log.warn("Парсинг ссылки не разрешен robots.txt: {}", link);
      throw new IllegalStateException("Парсинг ссылки не разрешен!");
    }

    try {
      semaphore.acquire();

      var response = restTemplate.execute(
        link,
        HttpMethod.GET,
        request -> {
          request.getHeaders().add("User-Agent", "RikserBot/1.0");
        },
        crawlerResponseExtractor,
        String.class
      );

      var isCaptcha = CaptchaUtils.isCaptcha(response);
      if (isCaptcha) {
        log.warn("Обнаружена капча по ссылке {}, перемещено в очередь для повторного запроса", link);
        addDelayProcess(requestDto);
        return null;
      }

      return response;

    } catch (BigSizeContentException e) {
      log.warn("Слишком большой размер скачиваемой страницы! url={}, error={}", link, e.getMessage(), e);
      throw new IllegalStateException("Слишком большой размер скачиваемой страницы!");
    } catch (Exception e) {
      log.warn("Проблемы со скачиванием по ссылке {}, error={}: {}", link, e.getClass().getSimpleName(), e.getMessage(), e);
      addDelayProcess(requestDto);
      return null;
    } finally {
      semaphore.release();
    }
  }

  private <T extends QueryResponseDtoCrawler> void addDelayProcess(T requestDto) {
    var delayedProcess = new DelayedQueryResponseDtoCrawler();
    delayedProcess.setSearchResponse(requestDto.getSearchResponse());
    delayedProcess.setAttempt(requestDto.getAttempt() + 1);

    var randomPercent = random.nextInt(RANDOM_BOUND);
    var repeatDownloadDelay = fetchProperties.getRepeatDownloadDelay();
    var delayTime = repeatDownloadDelay + (repeatDownloadDelay / 100 * randomPercent);

    var sameDomainCount = delayQueue.stream()
      .filter(response -> response.getSearchResponse().getDomain()
        .equals(requestDto.getSearchResponse().getDomain()))
      .count();

    if (sameDomainCount > 0) {
      delayTime += repeatDownloadDelay * sameDomainCount;
    }

    delayedProcess.setDelayInSeconds(delayTime);

    delayQueue.add(delayedProcess);
  }

  private boolean isParsingAllowed(String link) {
    URI url;

    try {
      url = new URI(link);
    } catch (Exception e) {
      return false;
    }

    var domain = url.getHost();
    var robotsLink = url.getScheme() + "://" + domain + "/robots.txt";

    var robotsResponse = redisCacheService.get(domain, String.class)
      .orElseGet(() -> {
        var response = downloadDomainRobotsFile(domain, robotsLink);
        return response;
      });

    if (StringUtils.isEmpty(robotsResponse)) {
      return true;
    }

    var robotsRulesParser = new SimpleRobotRulesParser();
    var rules = robotsRulesParser.parseContent(
      robotsLink,
      robotsResponse.getBytes(StandardCharsets.UTF_8),
      "text/plain",
      "CrawlerBot/1.0"
    );

    var allowed = rules.isAllowed(link);
    return allowed;
  }

  private String downloadDomainRobotsFile(String domain, String robotsLink) {
    try {
      var response = restTemplate.getForEntity(robotsLink, String.class);
      var status = response.getStatusCode();

      if (StringUtils.isEmpty(response.getBody())) {
        return null;
      }

      if (!status.equals(HttpStatus.OK) && !status.equals(HttpStatus.NO_CONTENT)) {
        return null;
      }

      var body = response.getBody();

      redisCacheService.put(domain, body);
      return body;
    } catch (Exception e) {
      log.warn("Failed to download robots.txt for {}: {}: {}", robotsLink, e.getClass().getSimpleName(), e.getMessage(), e);
      return null;
    }
  }

  private <T extends QueryResponseDtoCrawler> SearchResponseDtoWithContent prepareRequestsWithContent(
    T request,
    String content
  ) {
    var requestWithContent = new SearchResponseDtoWithContent();
    requestWithContent.setSearchResponse(request.getSearchResponse());
    requestWithContent.setContent(content);

    return requestWithContent;
  }

  private void publishFinishDownloadContentEvent(SearchResponseDtoWithContent content) {
    var finishEvent = new FinishDownloadContentEvent();
    finishEvent.setDto(content);
    eventPublisher.publishEvent(finishEvent);
  }
}