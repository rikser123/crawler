package rikser123.crawler.service;

import crawlercommons.robots.SimpleRobotRulesParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import rikser123.bundle.service.RedisCacheService;
import rikser123.crawler.component.CrawlerResponseExtractor;
import rikser123.crawler.component.PrometheusMetrics;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.queryResponse.QueryResponseDto;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;
import rikser123.crawler.exception.BigSizeContentException;
import rikser123.crawler.utils.CaptchaUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;


@RequiredArgsConstructor
@Service
@Slf4j
public class Crawler {
  private static final Random random = new Random();
  private static final Integer RANDOM_BOUND = 30;

  private final FetchConfigProperties fetchProperties;
  private final CrawlerResponseExtractor crawlerResponseExtractor;
  private final RestTemplate restTemplate;
  private final RedisCacheService redisCacheService;
  private final PrometheusMetrics prometheusMetrics;

  private ConcurrentHashMap<String, AtomicInteger> processedResponses = new ConcurrentHashMap<>();

  public SearchResponseDtoWithContent download(QueryResponseDto queryResponseDto) {
    var domain = queryResponseDto.getDomain();
    processedResponses.computeIfAbsent(domain, k -> new AtomicInteger(0)).incrementAndGet();

    try {
      var sameDomainCount = processedResponses.get(domain).get();
      if (sameDomainCount > 1) {
        var delay = getDelay(queryResponseDto);
        Thread.sleep(delay);
      }
    } catch (Exception e) {
      Thread.currentThread().interrupt();
    }

    var attempt = 0;

    while (attempt < fetchProperties.getMaxDownloadAttempt()) {
      attempt += 1;
      var delay = 0;

      try {
        var content = downloadLinkContent(queryResponseDto);

        if (!Objects.isNull(content)) {
          prometheusMetrics.incrementFinishDownload();

          var dto = new SearchResponseDtoWithContent();
          dto.setSearchResponse(queryResponseDto);
          dto.setContent(content);
          return dto;
        }

        delay = getDelay(queryResponseDto);
      } catch (IllegalStateException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException(e.getMessage(), e);
      } catch (Exception e) {
        if (attempt >= fetchProperties.getMaxDownloadAttempt()) {
          throw new IllegalStateException(e.getMessage(), e);
        }
        delay = getDelay(queryResponseDto);
      } finally {
        if (delay > 0) {
          try {
            Thread.sleep(delay);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        } else {
          processedResponses.compute(domain, (key, value) -> {
            if (value == null) return null;
            int newValue = value.decrementAndGet();
            return newValue == 0 ? null : value;
          });
        }
      }
    }

    throw new IllegalStateException("Превышен лимит попыток скачивания!");
  }

  private String downloadLinkContent(QueryResponseDto resultDto) {
    var link = resultDto.getUrl();

    var isAllowed = isParsingAllowed(link);

    if (!isAllowed) {
      log.warn("Парсинг ссылки не разрешен robots.txt: {}", link);
      throw new IllegalStateException("Парсинг ссылки не разрешен!");
    }

    try {
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
        return null;
      }

      return response;

    } catch (BigSizeContentException e) {
      log.warn("Слишком большой размер скачиваемой страницы! url={}, error={}", link, e.getMessage(), e);
      throw new IllegalStateException("Слишком большой размер скачиваемой страницы!");
    } catch (Exception e) {
      log.warn("Проблемы со скачиванием по ссылке {}, error={}: {}", link, e.getClass().getSimpleName(), e.getMessage(), e);
      return null;
    }
  }

  private int getDelay(QueryResponseDto queryResponseDto) {
    var randomPercent = random.nextInt(RANDOM_BOUND);
    var repeatDownloadDelay = fetchProperties.getRepeatDownloadDelay();
    var shift = repeatDownloadDelay / 100 * randomPercent;
    var delayTime = repeatDownloadDelay + shift;

    var sameDomainCount = processedResponses.get(queryResponseDto.getDomain()).get();

    if (sameDomainCount > 1) {
      delayTime += repeatDownloadDelay + (shift * sameDomainCount);
    }

    return delayTime;
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
      log.warn("Failed to download robots.txt for {}", robotsLink);
      return null;
    }
  }

}