package rikser123.crawler.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import rikser123.crawler.dto.queryResponse.QueryResponseDto;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;
import rikser123.crawler.dto.userQuery.QueryAnalysisDto;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

@ExtendWith(SpringExtension.class)
public class QueryAnalysisTest {
  private QueryAnalyzer queryAnalyzer;

  @Mock
  private LlmService llmService;

  @BeforeEach
  void init() {
    queryAnalyzer = new QueryAnalyzer(llmService);
  }

  @Test
  public void shouldAnalyse() {
    var dto = createAnalysisDto();
    when(llmService.getClusters(eq(dto.getQueryText()), eq(dto.getContents()))).thenReturn(getClustersJson());
    when(llmService.getAnalysisCluster(eq(dto.getQueryText()), any(String.class), any())).thenReturn("cluster");
    when(llmService.getClustersSynthesis(eq(dto.getQueryText()), eq(List.of("cluster", "cluster")))).thenReturn("synthesis");
    when(llmService.getCritic(eq(dto.getQueryText()), eq("synthesis"))).thenReturn("critics");
    when(llmService.getAnalysis(eq(dto.getQueryText()), eq("synthesis"), eq("critics"), eq(List.of("cluster", "cluster")))).thenReturn("analysis");

    var result = queryAnalyzer.makeAnalysis(dto);
    assertThat(result.getAnalysis()).isEqualTo("analysis");
  }

  @Test
  public void shouldHandleError() {
    var dto = createAnalysisDto();
    when(llmService.getClusters(eq(dto.getQueryText()), eq(dto.getContents()))).thenThrow(new IllegalStateException("ex"));

    assertThatThrownBy(() -> queryAnalyzer.makeAnalysis(dto))
      .isInstanceOf(IllegalStateException.class);

  }

  private static QueryAnalysisDto createAnalysisDto() {
    var dto = new QueryAnalysisDto();
    dto.setUserId(UUID.randomUUID());
    dto.setSearchQueryId(UUID.randomUUID());
    dto.setQueryText("queryText");

    var searchResponse = new QueryResponseDto();
    searchResponse.setUrl("url");
    searchResponse.setSearchResponseId(UUID.randomUUID());
    searchResponse.setDomain("domain");
    searchResponse.setPassages("passages");
    var searchResponseWithContent = new SearchResponseDtoWithContent();
    searchResponseWithContent.setSearchResponse(searchResponse);
    searchResponseWithContent.setContent("content1");

    var searchResponse2 = new QueryResponseDto();
    searchResponse2.setUrl("url2");
    searchResponse2.setSearchResponseId(UUID.randomUUID());
    searchResponse2.setDomain("domain");
    searchResponse2.setPassages("passages2");
    var searchResponseWithContent2 = new SearchResponseDtoWithContent();
    searchResponseWithContent2.setContent("content2");
    searchResponseWithContent2.setSearchResponse(searchResponse2);

    dto.setContents(List.of(searchResponseWithContent, searchResponseWithContent2));

    return dto;
  }

  private String getClustersJson() {
    return """
      ```json
      {
        "clusters": [
          {
            "name": "Регулирование криптовалют в России",
            "essence": "Обсуждение правового статуса цифровых активов, позиции ЦБ и Минфина, а также рисков технологического отставания при отказе от регулирования.",
            "sources": [
              "url"             
            ],
            "key_claims": [
              "Правовой статус криптовалюты как имущества не меняется при введении регулирования"           
            ]
          },
          {
            "name": "Прогнозы цены и рыночные циклы",
            "essence": "Мнения аналитиков и блогеров о вероятной динамике биткоина и альткоинов, привязка к макроэкономическим событиям и заседаниям ФРС.",
            "sources": [
              "url2"           
            ],
            "key_claims": [
              "После сигнала ФРС криптовалюты в среднем приносили до 250% прибыли"           
            ]
          }       
        ]
      }```
      """;
  }
}
