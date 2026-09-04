package rikser123.crawler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.queryResponse.QueryResponseDto;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithChunks;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(SpringExtension.class)
public class SummariserTest {
  private Summariser summariser;

  @Mock
  private LlmService llmService;

  @BeforeEach
  void init() {
    var fetchConfig = new FetchConfigProperties();
    fetchConfig.setRepeatDownloadDelay(1);
    fetchConfig.setMaxDownloadAttempt(2);
    fetchConfig.setQueueLimit(5);
    fetchConfig.setTimeoutQueueLimit(5);

    summariser = new Summariser(fetchConfig, llmService);
  }

  @Test
  void shouldFindRelevantChunks() {
    var dto = new SearchResponseDtoWithChunks();
    var searchResponse = new QueryResponseDto();
    searchResponse.setQueryText("Текст");
    dto.setSearchResponse(searchResponse);
    dto.setAttempt(0);
    dto.setChunks(List.of("Эй вы там", "Текст запроса", "Текст ответа"));

    when(llmService.getSummary(any())).thenReturn("outputText");

    summariser.summarise(dto);

    verify(llmService, atLeastOnce()).getSummary(argThat(arg -> {
      assertThat(arg).contains("Текст запроса");
      return true;
    }));
  }

  @Test
  void shouldHandleTwoCHunksWithoutComparingWithQuery() {
    var dto = new SearchResponseDtoWithChunks();
    var searchResponse = new QueryResponseDto();
    searchResponse.setQueryText("Текст");
    dto.setSearchResponse(searchResponse);
    dto.setAttempt(0);
    dto.setChunks(List.of("Эй вы там", "Текст запроса"));

    when(llmService.getSummary(any())).thenReturn("outputText");

    summariser.summarise(dto);

    verify(llmService, atLeastOnce()).getSummary(argThat(arg -> {
      assertThat(arg).contains("Эй вы там");
      assertThat(arg).contains("Текст запроса");
      return true;
    }));
  }

  @Test
  void shouldSendErrorMessageIfNoChunks() {
    var dto = new SearchResponseDtoWithChunks();
    var searchResponse = new QueryResponseDto();
    searchResponse.setQueryText("aaaaaaaaaaaaaaa");
    dto.setSearchResponse(searchResponse);
    dto.setAttempt(0);
    dto.setChunks(List.of("Эй вы там", "Эй вы там", "Эй вы там"));

    assertThatThrownBy(() ->  summariser.summarise(dto)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void shouldSendErrorIfBothubUnavailable() {
    var dto = new SearchResponseDtoWithChunks();
    var searchResponse = new QueryResponseDto();
    searchResponse.setQueryText("aaaaaaaaaaaaaaa");
    dto.setSearchResponse(searchResponse);
    dto.setAttempt(0);
    dto.setChunks(List.of("Эй вы там", "Эй вы там"));

    when(llmService.getSummary(any())).thenThrow(new IllegalStateException("Не удалось получить данные из Bothub"));

    assertThatThrownBy(() ->  summariser.summarise(dto)).isInstanceOf(IllegalStateException.class);
  }
}
