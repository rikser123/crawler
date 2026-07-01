package rikser123.crawler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import rikser123.crawler.component.EventPublisher;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.BothubResponseDto;
import rikser123.crawler.dto.SearchResponseDto;
import rikser123.crawler.dto.SearchResponseDtoWithChunks;
import rikser123.crawler.feign.BothubClient;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(SpringExtension.class)
public class SummariserTest {
  private Summariser summariser;

  @Mock
  private EventPublisher eventPublisher;

  @Mock
  private BothubClient bothubClient;

  @BeforeEach
  void init() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    var fetchConfig = new FetchConfigProperties();
    fetchConfig.setRepeatDownloadDelay(1);
    fetchConfig.setMaxDownloadAttempt(2);
    fetchConfig.setQueueLimit(5);
    fetchConfig.setTimeoutQueueLimit(5);

    summariser = new Summariser(eventPublisher, bothubClient, fetchConfig);

    var initMethod = Summariser.class.getDeclaredMethod("init");
    initMethod.setAccessible(true);
    initMethod.invoke(summariser);
    initMethod.setAccessible(false);
  }

  @Test
  void shouldFindRelevantChunks() {
    var dto = new SearchResponseDtoWithChunks();
    var searchResponse = new SearchResponseDto();
    searchResponse.setQueryText("Текст");
    dto.setSearchResponse(searchResponse);
    dto.setAttempt(0);
    dto.setChunks(List.of("Эй вы там", "Текст запроса", "Текст ответа"));

    var bothubResponse = new BothubResponseDto();
    bothubResponse.setOutputText("outputText");

    when(bothubClient.getResponses(any(), any())).thenReturn(bothubResponse);

    summariser.initSummarising(dto);

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(bothubClient, atLeastOnce()).getResponses(argThat(arg -> {
          assertThat(arg.getInput()).contains("Текст запроса");
          assertThat(arg.getInput()).contains("Текст ответа");
          return true;
        }), any());
      });
  }

  @Test
  void shouldHandleTwoCHunksWithoutComparingWithQuery() {
    var dto = new SearchResponseDtoWithChunks();
    var searchResponse = new SearchResponseDto();
    searchResponse.setQueryText("Текст");
    dto.setSearchResponse(searchResponse);
    dto.setAttempt(0);
    dto.setChunks(List.of("Эй вы там", "Текст запроса"));

    var bothubResponse = new BothubResponseDto();
    bothubResponse.setOutputText("outputText");

    when(bothubClient.getResponses(any(), any())).thenReturn(bothubResponse);

    summariser.initSummarising(dto);

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(bothubClient, atLeastOnce()).getResponses(argThat(arg -> {
          assertThat(arg.getInput()).contains("Эй вы там");
          assertThat(arg.getInput()).contains("Текст запроса");
          return true;
        }), any());
      });
  }

  @Test
  void shouldSendErrorMessageIfNoChunks() {
    var dto = new SearchResponseDtoWithChunks();
    var searchResponse = new SearchResponseDto();
    searchResponse.setQueryText("aaaaaaaaaaaaaaa");
    dto.setSearchResponse(searchResponse);
    dto.setAttempt(0);
    dto.setChunks(List.of("Эй вы там", "Эй вы там", "Эй вы там"));

    summariser.initSummarising(dto);

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce()).publishResponseProcessingErrorEvent(any(), any());
      });
  }

  @Test
  void shouldSendErrorIfBothubUnavailable() {
    var dto = new SearchResponseDtoWithChunks();
    var searchResponse = new SearchResponseDto();
    searchResponse.setQueryText("aaaaaaaaaaaaaaa");
    dto.setSearchResponse(searchResponse);
    dto.setAttempt(0);
    dto.setChunks(List.of("Эй вы там", "Эй вы там"));

    var bothubResponse = new BothubResponseDto();
    bothubResponse.setOutputText("outputText");
    bothubResponse.setError(new HashMap<>());

    when(bothubClient.getResponses(any(), any())).thenReturn(bothubResponse);

    summariser.initSummarising(dto);

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce()).publishResponseProcessingErrorEvent(any(), any());
      });
  }
}
