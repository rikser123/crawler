package rikser123.crawler.service;

import crawlercommons.robots.SimpleRobotRulesParser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import rikser123.bundle.service.RedisCacheService;
import rikser123.crawler.component.CrawlerResponseExtractor;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.DelayedProcessedSearchResponseDto;
import rikser123.crawler.dto.MessageSearchResponseDto;
import rikser123.crawler.dto.ProcessedSearchResponseDto;
import rikser123.crawler.dto.SearchResponseDtoWithContent;
import rikser123.crawler.dto.event.FinishDownloadContentEvent;
import rikser123.crawler.dto.event.ResponseProcessingErrorEvent;
import rikser123.crawler.exception.BigSizeContentException;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

@RequiredArgsConstructor
@Service
@Slf4j
public class Crawler {
  private static final Executor executors = Executors.newVirtualThreadPerTaskExecutor();
  private static final Random random = new Random();
  private static final Integer RANDOM_BOUND = 30;

  private static final BlockingQueue<ProcessedSearchResponseDto> queue = new LinkedBlockingQueue<>();
  private static final DelayQueue<DelayedProcessedSearchResponseDto> delayQueue = new DelayQueue<>();
  private static Semaphore queueSemaphore;
  private static Semaphore delayQueueSemaphore;

  private final FetchConfigProperties fetchProperties;
  private final CrawlerResponseExtractor crawlerResponseExtractor;
  private final ApplicationEventPublisher eventPublisher;
  private final RestTemplate restTemplate;
  private final RedisCacheService redisCacheService;

  @PostConstruct
  void init() {
    queueSemaphore = new Semaphore(fetchProperties.getQueueLimit());
    delayQueueSemaphore = new Semaphore(fetchProperties.getTimeoutQueueLimit());

    initThreadPool(queue, queueSemaphore);
    initThreadPool(delayQueue, delayQueueSemaphore);
  }

  public void initDownloading(MessageSearchResponseDto resultDto) {
    var requestDto = new ProcessedSearchResponseDto();
    requestDto.setAttempt(0);
    requestDto.setSearchResponse(resultDto);

    var sameDomainCount = queue.stream().filter(response -> response.getSearchResponse().getDomain().equals(resultDto.getDomain())).count();
    if (sameDomainCount > 0) {
      addDelayProcess(requestDto);
    } else {
      queue.add(requestDto);
    }
  }

  private <T extends  ProcessedSearchResponseDto>void initThreadPool(BlockingQueue<T> queue, Semaphore semaphore) {
    executors.execute(() -> {
      while (true) {
        UUID requestResultId = null;
        try {
          var request = queue.take();
          requestResultId = request.getSearchResponse().getSearchResponseId();
          executors.execute(() -> {
            var content = downloadLinkContent(request, semaphore);
            var requestWithContent = prepareRequestsWithContent(request, content);
            publishFinishDownloadContentEvent(requestWithContent);
          });
        } catch (IllegalStateException ex) {
          publishErrorEvent(requestResultId,ex.getMessage());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
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
    var delayTime = fetchProperties.getRepeatDownloadDelay() + (fetchProperties.getRepeatDownloadDelay() / 100 * randomPercent);
    var sameDomainCount = delayQueue.stream()
      .filter(response -> response.getSearchResponse().getDomain().equals(requestDto.getSearchResponse().getDomain()))
      .count();

    if (sameDomainCount > 0) {
      delayTime += fetchProperties.getRepeatDownloadDelay() * sameDomainCount;
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

  private void publishErrorEvent(UUID searchResponseId, String errorMessage) {
    var errorEvent = new ResponseProcessingErrorEvent();
    errorEvent.setMessage(errorMessage);
    errorEvent.setSearchResponseId(searchResponseId);
    eventPublisher.publishEvent(errorEvent);
  }
}
