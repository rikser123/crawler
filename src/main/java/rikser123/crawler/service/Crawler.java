package rikser123.crawler.service;

import crawlercommons.robots.SimpleRobotRules;
import crawlercommons.robots.SimpleRobotRulesParser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import rikser123.crawler.component.CrawlerResponseExtractor;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.DelayedProcessRequestDto;
import rikser123.crawler.dto.KafkaMessageRequestResultDto;
import rikser123.crawler.dto.ProcessedRequestDto;
import rikser123.crawler.dto.RequestWithContent;
import rikser123.crawler.exception.BigSizeContentException;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.stream.Stream;

@RequiredArgsConstructor
@Service
@Slf4j
public class Crawler {
  private static final Map<String, SimpleRobotRules> domainRobotRules = new ConcurrentHashMap<>();
  private static final RestTemplate restTemplate = new RestTemplate();
  private static final Executor executors = Executors.newVirtualThreadPerTaskExecutor();

  private static final BlockingQueue<ProcessedRequestDto> queue = new LinkedBlockingQueue<>();
  private static final DelayQueue<DelayedProcessRequestDto> delayQueue = new DelayQueue<>();
  private static final Map<UUID, ProcessedRequestDto> sameAsProcessingRequestsByLink = new ConcurrentHashMap<>();
  private static Semaphore queueSemaphore;
  private static Semaphore delayQueueSemaphore;

  private final FetchConfigProperties fetchProperties;
  private final CrawlerResponseExtractor crawlerResponseExtractor;

  @PostConstruct
  void init() {
    queueSemaphore = new Semaphore(fetchProperties.getQueueLimit());
    delayQueueSemaphore = new Semaphore(fetchProperties.getTimeoutQueueLimit());

    executors.execute(() -> {
      while (true) {
        try {
          var request = queue.take();
          executors.execute(() -> prepareRequestsWithContent(request, queueSemaphore));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    });

    executors.execute(() -> {
      while (true) {
        try {
          var request = delayQueue.take();
          executors.execute(() -> prepareRequestsWithContent(request,delayQueueSemaphore));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    });
  }

  public void initDownloading(KafkaMessageRequestResultDto resultDto) {
    var requestDto = new ProcessedRequestDto();
    requestDto.setAttempt(0);
    requestDto.setRequest(resultDto);

    var isInProcessing = isProcessingRequest(requestDto);
    if (isInProcessing) {
      sameAsProcessingRequestsByLink.put(resultDto.getRequestResultId(), requestDto);
    } else {
      queue.add(requestDto);
    }
  }


  private <T extends ProcessedRequestDto> String  downloadLinkContent(T requestDto, Semaphore semaphore) {
    var link = requestDto.getRequest().getUrl();

    if (requestDto.getAttempt() >= fetchProperties.getMaxDownloadAttempt()) {
      log.warn("Превышен лимит попыток скачивания {}", link);
      throw new IllegalStateException("Превышен лимит попыток скачивания!");
    }

    var isAllowed = isParsingAllowed(requestDto.getRequest().getUrl());

    if (!isAllowed) {
      log.warn("Парсинг ссылки не разрешен {}", link);
      throw new IllegalStateException("Парсинг ссылки не разрешен!");
    }

    try {
      semaphore.acquire();
      var response = restTemplate.execute(
        link,
        HttpMethod.GET,
        null,
        crawlerResponseExtractor,
        String.class
      );
      return response;
    } catch (BigSizeContentException e) {
      log.warn("Слишком большой размер скачиваемой страницы!", e);
      throw new IllegalStateException("Слишком большой размер скачиваемой страницы!");
    } catch (Exception e) {
      log.warn("Проблемы со скачиванием по ссылке {}, перемещено в очередь для повторного запроса", link);
      var delayedProcess = new DelayedProcessRequestDto();
      delayedProcess.setRequest(requestDto.getRequest());
      delayedProcess.setAttempt(requestDto.getAttempt() + 1);
      delayedProcess.setDelayInSeconds(fetchProperties.getRepeatDownloadDelay());
      delayQueue.add(delayedProcess);
      return null;
    } finally {
      semaphore.release();
    }
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

    if (domainRobotRules.containsKey(domain)) {
      return domainRobotRules.get(domain).isAllowed(link);
    }

    var robotsLink = url.getScheme() + "://" + domain + "/robots.txt";

    var robotsResponse = restTemplate.getForEntity(robotsLink, String.class);

    if (StringUtils.isEmpty(robotsResponse.getBody())) {
      return true;
    }

    var status = robotsResponse.getStatusCode();
    if (!status.equals(HttpStatus.OK) && !status.equals(HttpStatus.NO_CONTENT)) {
      return true;
    }

    var robotsRulesParser = new SimpleRobotRulesParser();
    var rules = robotsRulesParser.parseContent(
      robotsLink,
      robotsResponse.getBody().getBytes(StandardCharsets.UTF_8),
      "text/plain",
      "CrawlerBot/1.0"
    );
    domainRobotRules.put(domain, rules);

    return rules.isAllowed(link);
  }

  private boolean isProcessingRequest(ProcessedRequestDto requestDto) {
    var requestId = requestDto.getRequest().getRequestResultId();

    var isInQueue = queue.stream().anyMatch(req -> req.getRequest().getRequestResultId().equals(requestId));
    var isInDelayQueue = delayQueue.stream().anyMatch(req -> req.getRequest().getRequestResultId().equals(requestId));

    return Stream.of(
      isInQueue,
      isInDelayQueue
    ).anyMatch(Boolean::booleanValue);
  }

  private List<ProcessedRequestDto> findWaitingRequests(String link) {
    var processingRequests = new ArrayList<ProcessedRequestDto>();
    sameAsProcessingRequestsByLink.entrySet().forEach(entry -> {
      var value = entry.getValue();
      var isSameLink = entry.getValue().getRequest().getUrl().equals(link);
      if (isSameLink) {
        processingRequests.add(value);
      }
    });

    return processingRequests;
  }

  private <T extends ProcessedRequestDto> List<RequestWithContent> prepareRequestsWithContent(
    T request,
    Semaphore semaphore
  ) {
    var requests = new ArrayList<RequestWithContent>();
    var waitingRequests = findWaitingRequests(request.getRequest().getUrl());
    String content;

    try {
      content = downloadLinkContent(request, semaphore);
    } catch (IllegalStateException ex) {
      throw new IllegalStateException(ex.getMessage(), ex);
    } finally {
      waitingRequests.forEach(req -> {
        sameAsProcessingRequestsByLink.remove(req.getRequest().getRequestResultId());
      });
    }

    var requestWithContent = new RequestWithContent();
    requestWithContent.setRequestResult(request.getRequest());
    requestWithContent.setContent(content);
    requests.add(requestWithContent);

    var waitingRequestsContent = waitingRequests.stream().map(req -> {
      var watingRequestWithContent = new RequestWithContent();
      watingRequestWithContent.setContent(content);
      watingRequestWithContent.setRequestResult(req.getRequest());
      return watingRequestWithContent;
    }).toList();
    requests.addAll(waitingRequestsContent);
    return requests;
  }
}
