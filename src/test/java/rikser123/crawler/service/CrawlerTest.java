package rikser123.crawler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import rikser123.crawler.dto.queryResponse.QueryResponseDto;

import java.util.UUID;
import org.springframework.web.client.RestTemplate;
import rikser123.bundle.service.RedisCacheService;
import rikser123.crawler.component.CrawlerResponseExtractor;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.exception.BigSizeContentException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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

  @BeforeEach
  void init() {
    var fetchConfig = new FetchConfigProperties();
    fetchConfig.setQueueLimit(5);
    fetchConfig.setTimeoutQueueLimit(5);
    fetchConfig.setMaxDownloadAttempt(2);
    fetchConfig.setRepeatDownloadDelay(1);

    crawler = new Crawler(
      fetchConfig,
      crawlerResponseExtractor,
      restTemplate,
      redisCacheService
    );
  }

  @Test
  void shouldSuccessFetch() {
    var responseDto = createResponseDto();

    when(restTemplate.execute(any(), any(), any(), any(), eq(String.class))).thenReturn("string");
    when(restTemplate.getForEntity(anyString(), any())).thenReturn(ResponseEntity.ok().body(""));

    var result = crawler.download(responseDto);
    assertThat(result.getContent()).isEqualTo("string");
    assertThat(result.getSearchResponse().getSearchResponseId()).isEqualTo(responseDto.getSearchResponseId());
  }

  @Test
  void shouldHandleMaxDownloadAttempt() {
    var responseDto = createResponseDto();

    when(restTemplate.execute(any(), any(), any(), any(), eq(String.class))).thenThrow(new RuntimeException());
    when(restTemplate.getForEntity(anyString(), any())).thenReturn(ResponseEntity.ok().body(""));

    assertThatThrownBy(() -> crawler.download(responseDto)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldHandleParsingNotAllowed() {
    var responseDto = createResponseDto();

    when(restTemplate.getForEntity(anyString(), any())).thenReturn(ResponseEntity.ok().body("User-agent: *\nDisallow: /"));

    assertThatThrownBy(() -> crawler.download(responseDto)).isInstanceOf(IllegalStateException.class);
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

    assertThatThrownBy(() -> crawler.download(responseDto)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldHandleCaptcha()  {
    var responseDto = createResponseDto();

    when(restTemplate.execute(any(), any(), any(), any(), eq(String.class))).thenReturn("cf-browser-verification");
    when(restTemplate.getForEntity(anyString(), any())).thenReturn(ResponseEntity.ok().body(""));

    assertThatThrownBy(() -> crawler.download(responseDto)).isInstanceOf(IllegalStateException.class);

  }

  private static QueryResponseDto createResponseDto() {
    var searchResponseDto = new QueryResponseDto();
    searchResponseDto.setSearchResponseId(UUID.randomUUID());
    searchResponseDto.setUrl(("url"));
    searchResponseDto.setDomain("domain");
    searchResponseDto.setQueryText("text");
    searchResponseDto.setQueryId(UUID.randomUUID());

    return searchResponseDto;
  }
}
