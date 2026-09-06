package rikser123.crawler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import rikser123.crawler.component.PrometheusMetrics;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.queryResponse.QueryResponseDto;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

@ExtendWith(SpringExtension.class)
public class ChunkSplitterTest {
  private ChunkSplitter chunkSplitter;

  @Mock
  private PrometheusMetrics prometheusMetrics;

  @BeforeEach
  void init() {
    var fetchConfig = new FetchConfigProperties();
    fetchConfig.setChunkSize(2000);
    fetchConfig.setWordOverlapCount(200);
    chunkSplitter = new ChunkSplitter(fetchConfig, prometheusMetrics);
  }

  @Test
  void shouldSuccessProcessShortChunk() {
    var content = "Content";
    var result = chunkSplitter.split(createSearchDto(content));
    assertThat(result.getChunks()).hasSize(1);

  }

  @Test
  void shouldSuccessProcessLongParagraphs() {
    var content = generateContent(3, 1000);
    var result = chunkSplitter.split(createSearchDto(content));
    assertThat(result.getChunks().size() > 2).isTrue();
  }

  @Test
  void shouldSuccessProcessLongSentence() {
    var content = generateContent(1, 1000);
    var result = chunkSplitter.split(createSearchDto(content));
    assertThat(result.getChunks().size() > 2).isTrue();
  }

  @Test
  void shouldHandleErrorIfTextIsEmpty() {
    assertThatThrownBy(() -> chunkSplitter.split(createSearchDto("")))
      .isInstanceOf(IllegalStateException.class);
  }

  private static SearchResponseDtoWithContent createSearchDto(String content) {
    var dto = new SearchResponseDtoWithContent();
    var searchResponse = new QueryResponseDto();
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
