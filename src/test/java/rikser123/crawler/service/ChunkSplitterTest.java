package rikser123.crawler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import rikser123.crawler.component.EventPublisher;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.SearchResponseDto;
import rikser123.crawler.dto.SearchResponseDtoWithContent;
import rikser123.crawler.dto.event.FinishSplitChunksEvent;
import rikser123.crawler.dto.event.ResponseProcessingErrorEvent;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;

import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;


import java.lang.reflect.InvocationTargetException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@ExtendWith(SpringExtension.class)
public class ChunkSplitterTest {
  private ChunkSplitter chunkSplitter;

  @Mock
  private EventPublisher eventPublisher;

  @Captor
  private ArgumentCaptor<FinishSplitChunksEvent> eventCaptor;

  @Captor
  private ArgumentCaptor<ResponseProcessingErrorEvent> eventErrorCaptor;

  @BeforeEach
  void init() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
    var fetchConfig = new FetchConfigProperties();
    fetchConfig.setChunkSize(2000);
    fetchConfig.setWordOverlapCount(200);
    chunkSplitter = new ChunkSplitter(fetchConfig, eventPublisher);

    var initMethod = ChunkSplitter.class.getDeclaredMethod("init");
    initMethod.setAccessible(true);
    initMethod.invoke(chunkSplitter);
    initMethod.setAccessible(false);
  }

  @Test
  void shouldSuccessProcessShortChunk() {
    var content = "Content";
    chunkSplitter.initSpliting(createSearchDto(content));

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce())
          .publishEvent(eventCaptor.capture());

        var event = eventCaptor.getValue();
        assertThat(event.getDtoWithChunks().getChunks()).hasSize(1);
      });
  }

  @Test
  void shouldSuccessProcessLongParagraphs() {
    var content = generateContent(3, 1000);
    chunkSplitter.initSpliting(createSearchDto(content));

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce())
          .publishEvent(eventCaptor.capture());

        var event = eventCaptor.getValue();
        assertThat(event.getDtoWithChunks().getChunks().size() > 2).isTrue();
      });
  }

  @Test
  void shouldSuccessProcessLongSentence() {
    var content = generateContent(1, 1000);
    chunkSplitter.initSpliting(createSearchDto(content));

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce())
          .publishEvent(eventCaptor.capture());

        var event = eventCaptor.getValue();
        assertThat(event.getDtoWithChunks().getChunks().size() > 2).isTrue();
      });
  }

  @Test
  void shouldHandleErrorIfTextIsEmpty() {
    chunkSplitter.initSpliting(createSearchDto(""));

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce())
          .publishResponseProcessingErrorEvent(any(SearchResponseDto.class), any());
      });
  }

  private static SearchResponseDtoWithContent createSearchDto(String content) {
    var dto = new SearchResponseDtoWithContent();
    var searchResponse = new SearchResponseDto();
    searchResponse.setSearchResponseId(UUID.randomUUID());
    dto.setSearchResponse(searchResponse);
    dto.setContent(content);
    return dto;
  }

  private String generateContent(int paragraphsCount, int paragraphsSize) {
    var word = "word";
    var content = new StringBuilder();

    for (var i = 0; i < paragraphsCount; i +=1) {
      for (var j = 0; j < paragraphsSize; j +=1) {
        content.append(" " + word);
      }
      content.append("\n\n");
    }

    return content.toString();
  }
}
