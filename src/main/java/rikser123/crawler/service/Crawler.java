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
import rikser123.crawler.dto.DelayedProcessedSearchResponseDto;
import rikser123.crawler.dto.SearchResponseDto;
import rikser123.crawler.dto.ProcessedSearchResponseDto;
import rikser123.crawler.dto.SearchResponseDtoWithContent;
import rikser123.crawler.dto.event.FinishDownloadContentEvent;
import rikser123.crawler.exception.BigSizeContentException;
import rikser123.crawler.utils.CaptchaUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;

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
public class Crawler {
  private static final Random random = new Random();
  private static final Integer RANDOM_BOUND = 30;

  private final ExecutorService executors = Executors.newVirtualThreadPerTaskExecutor();
  private final BlockingQueue<ProcessedSearchResponseDto> queue = new LinkedBlockingQueue<>();
  private final DelayQueue<DelayedProcessedSearchResponseDto> delayQueue = new DelayQueue<>();
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

    initThreadPool(queue, queueSemaphore);
    initThreadPool(delayQueue, delayQueueSemaphore);
  }

  @PreDestroy
  public void shutdown() {
    log.info("Shutting down Crawler...");
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

  public void initDownloading(SearchResponseDto resultDto) {
    var requestDto = new ProcessedSearchResponseDto();
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

  private <T extends  ProcessedSearchResponseDto>void initThreadPool(BlockingQueue<T> queue, Semaphore semaphore) {
    executors.execute(() -> {
      while (true) {
        try {
          var request = queue.take();
          executors.execute(() -> {
            try {
              var content = downloadLinkContent(request, semaphore);
              var requestWithContent = prepareRequestsWithContent(request, content);
              publishFinishDownloadContentEvent(requestWithContent);
            } catch (IllegalStateException e) {
              eventPublisher.publishResponseProcessingErrorEvent(request.getSearchResponse(), e.getMessage());
            }
          });
        }  catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    });
  }

  private <T extends ProcessedSearchResponseDto> String  downloadLinkContent(T requestDto, Semaphore semaphore) {
    var link = requestDto.getSearchResponse().getUrl();

    if (requestDto.getAttempt() >= fetchProperties.getMaxDownloadAttempt()) {
      log.warn("Превышен лимит попыток скачивания {}", link);
      throw new IllegalStateException("Превышен лимит попыток скачивания!");
    }

    var isAllowed = isParsingAllowed(requestDto.getSearchResponse().getUrl());

    if (!isAllowed) {
      log.warn("Парсинг ссылки не разрешен {}", link);
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
        log.warn("Обнаружена капче по ссылке {}, перемещено в очередь для повторного запроса", link);
        addDelayProcess(requestDto);
        return null;
      }

      return response;
    } catch (BigSizeContentException e) {
      log.warn("Слишком большой размер скачиваемой страницы!", e);
      throw new IllegalStateException("Слишком большой размер скачиваемой страницы!");
    } catch (Exception e) {
      log.warn("Проблемы со скачиванием по ссылке {}, перемещено в очередь для повторного запроса", link);
      addDelayProcess(requestDto);
      return null;
    } finally {
      semaphore.release();
    }
  }

  private <T extends ProcessedSearchResponseDto> void addDelayProcess(T requestDto) {
    var delayedProcess = new DelayedProcessedSearchResponseDto();
    delayedProcess.setSearchResponse(requestDto.getSearchResponse());
    delayedProcess.setAttempt(requestDto.getAttempt() + 1);

    var randomPercent = random.nextInt(RANDOM_BOUND);
    var repeatDownloadDelay = fetchProperties.getRepeatDownloadDelay();
    var delayTime = repeatDownloadDelay + (repeatDownloadDelay / 100 * randomPercent);
    var sameDomainCount = delayQueue.stream()
      .filter(response -> response.getSearchResponse().getDomain().equals(requestDto.getSearchResponse().getDomain()))
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
        log.warn("incorrect url format {}", link, e);
        return false;
    }
    var domain = url.getHost();
    var robotsLink = url.getScheme() + "://" + domain + "/robots.txt";
    var robotsResponse = redisCacheService.get(domain, String.class)
      .orElseGet(() -> downloadDomainRobotsFile(domain, robotsLink));

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

    return rules.isAllowed(link);
  }

  private String downloadDomainRobotsFile(String domain, String robotsLink) {
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
  }

  private <T extends ProcessedSearchResponseDto> SearchResponseDtoWithContent prepareRequestsWithContent(
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
    finishEvent.setContext(content);
    eventPublisher.publishEvent(finishEvent);
  }
}
