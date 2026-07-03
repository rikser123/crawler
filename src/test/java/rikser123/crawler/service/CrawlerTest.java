package rikser123.crawler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestTemplate;
import rikser123.bundle.service.RedisCacheService;
import rikser123.crawler.component.CrawlerResponseExtractor;
import rikser123.crawler.component.EventPublisher;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.SearchResponseDto;
import rikser123.crawler.dto.SearchResponseDtoStatus;
import rikser123.crawler.dto.event.FinishDownloadContentEvent;
import rikser123.crawler.exception.BigSizeContentException;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
public class CrawlerTest {
  private Crawler crawler;

  @Mock
  private CrawlerResponseExtractor crawlerResponseExtractor;

  @Mock
  private RestTemplate restTemplate;

  @Mock
  private RedisCacheService redisCacheService;

  @Mock
  private EventPublisher eventPublisher;

  @BeforeEach
  void init() throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
    var fetchConfig = new FetchConfigProperties();
    fetchConfig.setQueueLimit(5);
    fetchConfig.setTimeoutQueueLimit(5);
    fetchConfig.setMaxDownloadAttempt(2);
    fetchConfig.setRepeatDownloadDelay(1);

    crawler = new Crawler(
      fetchConfig,
      crawlerResponseExtractor,
      restTemplate,
      redisCacheService,
      eventPublisher
    );

    var initMethod = Crawler.class.getDeclaredMethod("init");
    initMethod.setAccessible(true);
    initMethod.invoke(crawler);
    initMethod.setAccessible(false);
  }

  @Test
  void shouldSuccessFetch() {
    var responseDto = createResponseDto();

    when(restTemplate.execute(any(), any(), any(), any(), eq(String.class))).thenReturn("string");
    when(restTemplate.getForEntity(anyString(), any())).thenReturn(ResponseEntity.ok().body(""));

    crawler.initDownloading(responseDto);

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce()).publishEvent(argThat(arg -> {
          var event = (FinishDownloadContentEvent) arg;
          assertThat(event.getContext().getContent()).isEqualTo("string");
          assertThat(event.getContext().getSearchResponse().getSearchResponseId()).isEqualTo(responseDto.getSearchResponseId());
          return true;
        }));
      });
  }

  @Test
  void shouldHandleMaxDownloadAttempt() {
    var responseDto = createResponseDto();

    when(restTemplate.execute(any(), any(), any(), any(), eq(String.class))).thenThrow(new RuntimeException());
    when(restTemplate.getForEntity(anyString(), any())).thenReturn(ResponseEntity.ok().body(""));

    crawler.initDownloading(responseDto);

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce()).publishResponseProcessingErrorEvent(any(), any());
      });
  }

  @Test
  void shouldHandleParsingNotAllowed() {
    var responseDto = createResponseDto();

    when(restTemplate.getForEntity(anyString(), any())).thenReturn(ResponseEntity.ok().body("User-agent: *\nDisallow: /"));

    crawler.initDownloading(responseDto);

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce()).publishResponseProcessingErrorEvent(any(), any());
      });
  }

  @Test
  void shouldHandleBigSizeContent() throws IOException {
    var responseDto = createResponseDto();

    doThrow(new BigSizeContentException("Большой текст"))
      .when(crawlerResponseExtractor)
      .extractData(any());

    when(restTemplate.getForEntity(anyString(), any())).thenReturn(ResponseEntity.ok().body(""));
    when(restTemplate.execute(
      anyString(),
      any(),
      any(),
      eq(crawlerResponseExtractor),
      eq(String.class)
    )).thenAnswer(invocation -> {
      var extractor = invocation.getArgument(3, CrawlerResponseExtractor.class);
      return extractor.extractData(null);
    });

    crawler.initDownloading(responseDto);

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce()).publishResponseProcessingErrorEvent(any(), any());
      });
  }

  @Test
  void shouldHandleCaptcha()  {
    var responseDto = createResponseDto();

    when(restTemplate.execute(any(), any(), any(), any(), eq(String.class))).thenReturn("cf-browser-verification");
    when(restTemplate.getForEntity(anyString(), any())).thenReturn(ResponseEntity.ok().body(""));

    crawler.initDownloading(responseDto);

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce()).publishResponseProcessingErrorEvent(any(), any());
      });
  }

  private static SearchResponseDto createResponseDto() {
    var searchResponseDto = new SearchResponseDto();
    searchResponseDto.setSearchResponseId(UUID.randomUUID());
    searchResponseDto.setUrl(("url"));
    searchResponseDto.setDomain("domain");
    searchResponseDto.setQueryText("text");
    searchResponseDto.setQueryId(UUID.randomUUID());
    searchResponseDto.setStatus(SearchResponseDtoStatus.CREATED);

    return searchResponseDto;
  }
}
