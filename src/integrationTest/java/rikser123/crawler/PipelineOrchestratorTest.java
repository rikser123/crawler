package rikser123.crawler;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.client.RestTemplate;
import rikser123.bundle.service.RedisCacheService;
import rikser123.crawler.component.EventPublisher;
import rikser123.crawler.dto.userQuery.MessageUserQueryDto;
import rikser123.crawler.dto.event.FinishAnalysisEvent;
import rikser123.crawler.dto.event.FinishCleanContentEvent;
import rikser123.crawler.dto.event.FinishDownloadContentEvent;
import rikser123.crawler.dto.event.FinishSplitChunksEvent;
import rikser123.crawler.dto.event.SummaryEvent;
import rikser123.crawler.exception.BigSizeContentException;
import rikser123.crawler.service.BothubService;
import rikser123.crawler.service.PipelineOrchestrator;
import rikser123.crawler.service.SearchQueryMessageService;
import rikser123.crawler.service.SearchResponseMessageService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.atMostOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PipelineOrchestratorTest extends BaseConfig {
  @Autowired
  @MockitoSpyBean
  private EventPublisher eventPublisher;

  @Autowired
  private PipelineOrchestrator pipelineOrchestrator;

  @MockitoBean
  private RestTemplate restTemplate;

  @MockitoBean
  private RedisCacheService redisCacheService;

  @MockitoBean
  private BothubService bothubService;

  @Autowired
  @MockitoSpyBean
  private SearchQueryMessageService searchQueryMessageService;

  @Autowired
  @MockitoSpyBean
  private SearchResponseMessageService searchResponseMessageService;

  private static final String TEXT_CONTENT = """
        <!DOCTYPE html>
        <html lang="ru">
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <title>Тестовая страница</title>
        </head>
        <body>
            <h1>Тестовый контент</h1>          
            <p>Этот абзац содежит запрос от пользователя query text</p>       
            <p>Это первый абзац с дополнительным текстом для тестирования</p>        
            <p>Этот абзац содежит запрос от пользователя query text</p>         
            <p>Второй абзац с разным содержанием и словами</p>       
            <p>Этот абзац содежит запрос от пользователя query text</p>        
            <p>Третий абзац с еще каким-то текстом</p>        
            <div>
                <span>Этот абзац содежит запрос от пользователяquery text</span>
                <span>Этот абзац содежит запрос от пользователя еще текст внутри div</span>
            </div>
        </body>
        </html>
      """;

  private static final String CLEAN_TEXT = """
    Второй абзац с разным содержанием и словами
    Этот абзац содежит запрос от пользователя query text
    Третий абзац с еще каким-то текстом
    """;

  @Test
  void shouldHandleSuccessPipeline() {
    var messageDto = createMessageDto();
    var summary = "Краткий пересказ";
    var analysis = "Анализ";

    when(restTemplate.getForEntity(anyString(), any())).thenReturn(ResponseEntity.ok().body(""));
    when(restTemplate.execute(any(), any(), any(), any(), eq(String.class))).thenReturn(TEXT_CONTENT);
    when(bothubService.getSummary(any())).thenReturn(summary);
    when(bothubService.getQueryAnalysis(eq(messageDto.getQueryText()), any())).thenReturn(analysis);

    pipelineOrchestrator.initResponseProcessing(messageDto);

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce()).publishEvent(argThat(arg -> {
          if (!(arg instanceof FinishDownloadContentEvent)) {
            return false;
          }
          var event = (FinishDownloadContentEvent) arg;
          assertThat(event.getDto().getContent()).isEqualTo(TEXT_CONTENT);
          assertThat(event.getDto().getSearchResponse().getSearchResponseId()).isEqualTo(messageDto.getSearchResponses().getFirst().getSearchResponseId());
          return true;
        }));
      });

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce()).publishEvent(argThat(arg -> {
          if (!(arg instanceof FinishCleanContentEvent)) {
            return false;
          }
          var event = (FinishCleanContentEvent) arg;
          assertThat(event.getDto().getContent().strip()).isEqualTo(CLEAN_TEXT.strip());
          return true;
        }));
      });

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce()).publishEvent(argThat(arg -> {
          if (!(arg instanceof FinishSplitChunksEvent)) {
            return false;
          }
          var event = (FinishSplitChunksEvent) arg;
          assertThat(event.getDto().getChunks().getFirst().strip()).isEqualTo(CLEAN_TEXT.strip());
          return true;
        }));
      });

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce()).publishEvent(argThat(arg -> {
          if (!(arg instanceof SummaryEvent)) {
            return false;
          }
          var event = (SummaryEvent) arg;
          assertThat(event.getDto().getContent()).isEqualTo(summary);
          return true;
        }));
      });

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce()).publishEvent(argThat(arg -> {
          if (!(arg instanceof FinishAnalysisEvent)) {
            return false;
          }
          var event = (FinishAnalysisEvent) arg;
          assertThat(event.getDto().getAnalysis()).isEqualTo(analysis);
          return true;
        }));
      });

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(searchQueryMessageService, atLeastOnce()).createQueryOutboxSuccessMessage(argThat(arg -> {

          assertThat(arg.getAnalysis()).isEqualTo(analysis);
          return true;
        }));
      });
  }

  @Test
  void shouldHandleErrorCrawler() {
    var messageDto = createMessageDto();

    when(restTemplate.getForEntity(anyString(), any())).thenReturn(ResponseEntity.ok().body(""));
    when(restTemplate.execute(any(), any(), any(), any(), eq(String.class))).thenThrow(new BigSizeContentException("Большой текст"));


    pipelineOrchestrator.initResponseProcessing(messageDto);

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(searchResponseMessageService, atLeastOnce()).createOutboxRequestError(argThat(arg -> {
          assertThat(arg).isEqualTo(messageDto.getSearchResponses().getFirst().getSearchResponseId());
          return true;
        }), any());
      });

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(searchQueryMessageService, atLeastOnce()).createQueryOutboxErrorMessage(argThat(arg -> {
          assertThat(arg.getSearchQueryId()).isEqualTo(messageDto.getSearchQueryId());
          return true;
        }), any());
      });
  }

  @Test
  void shouldHandleOneTwoRequests() {
    var messageDto = createMessageDto();
    var response = new MessageUserQueryDto.SearchResponse();
    response.setSearchResponseId(UUID.randomUUID());
    response.setUrl("url2");
    response.setDomain("domain");
    messageDto.setSearchResponses(new ArrayList<>(messageDto.getSearchResponses()));
    messageDto.getSearchResponses().add(response);

    var summary = "Краткий пересказ";
    var analysis = "Анализ";

    when(restTemplate.getForEntity(anyString(), any())).thenReturn(ResponseEntity.ok().body(""));
    when(restTemplate.execute(eq("url"), any(), any(), any(), eq(String.class))).thenReturn(TEXT_CONTENT);
    when(restTemplate.execute(eq("url2"), any(), any(), any(), eq(String.class))).thenReturn(TEXT_CONTENT);

    when(bothubService.getSummary(any())).thenReturn(summary);
    when(bothubService.getQueryAnalysis(eq(messageDto.getQueryText()), any())).thenReturn(analysis);

    pipelineOrchestrator.initResponseProcessing(messageDto);

    await().atMost(10, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce()).publishEvent(argThat(arg -> {
          if (!(arg instanceof FinishAnalysisEvent)) {
            return false;
          }
          var event = (FinishAnalysisEvent) arg;
          assertThat(event.getDto().getAnalysis()).isEqualTo(analysis);
          return true;
        }));
      });

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(searchQueryMessageService, atLeastOnce()).createQueryOutboxSuccessMessage(argThat(arg -> {

          assertThat(arg.getAnalysis()).isEqualTo(analysis);
          return true;
        }));
      });
  }

  @Test
  void shouldHandleOneTwoRequestsWithErrors() {
    var messageDto = createMessageDto();
    var response = new MessageUserQueryDto.SearchResponse();
    response.setSearchResponseId(UUID.randomUUID());
    response.setUrl("url2");
    response.setDomain("domain");
    messageDto.setSearchResponses(new ArrayList<>(messageDto.getSearchResponses()));
    messageDto.getSearchResponses().add(response);

    var summary = "Краткий пересказ";
    var analysis = "Анализ";

    when(restTemplate.getForEntity(anyString(), any())).thenReturn(ResponseEntity.ok().body(""));
    when(restTemplate.execute(eq("url"), any(), any(), any(), eq(String.class))).thenReturn(TEXT_CONTENT);
    when(restTemplate.execute(eq("url2"), any(), any(), any(), eq(String.class))).thenThrow(new BigSizeContentException("Большой размер!"));

    when(bothubService.getSummary(any())).thenReturn(summary);
    when(bothubService.getQueryAnalysis(eq(messageDto.getQueryText()), any())).thenReturn(analysis);

    pipelineOrchestrator.initResponseProcessing(messageDto);

    await().atMost(10, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atLeastOnce()).publishEvent(argThat(arg -> {
          if (!(arg instanceof FinishAnalysisEvent)) {
            return false;
          }
          var event = (FinishAnalysisEvent) arg;
          assertThat(event.getDto().getAnalysis()).isEqualTo(analysis);
          return true;
        }));
      });

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(searchQueryMessageService, atLeastOnce()).createQueryOutboxSuccessMessage(argThat(arg -> {

          assertThat(arg.getAnalysis()).isEqualTo(analysis);
          return true;
        }));
      });
  }

  @Test
  void shouldHandleOnlyOnceSameUrls() {
    var messageDto = createMessageDto();
    var response = new MessageUserQueryDto.SearchResponse();
    response.setSearchResponseId(UUID.randomUUID());
    response.setUrl("url");
    response.setDomain("domain");
    messageDto.setSearchResponses(new ArrayList<>(messageDto.getSearchResponses()));
    messageDto.getSearchResponses().add(response);

    var summary = "Краткий пересказ";
    var analysis = "Анализ";

    when(restTemplate.getForEntity(anyString(), any())).thenReturn(ResponseEntity.ok().body(""));
    when(restTemplate.execute(eq("url"), any(), any(), any(), eq(String.class))).thenReturn(TEXT_CONTENT);
    when(restTemplate.execute(eq("url2"), any(), any(), any(), eq(String.class))).thenReturn(TEXT_CONTENT);

    when(bothubService.getSummary(any())).thenReturn(summary);
    when(bothubService.getQueryAnalysis(eq(messageDto.getQueryText()), any())).thenReturn(analysis);

    pipelineOrchestrator.initResponseProcessing(messageDto);

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(eventPublisher, atMostOnce()).publishEvent(argThat(arg -> {
          if (!(arg instanceof FinishDownloadContentEvent)) {
            return false;
          }
          var event = (FinishDownloadContentEvent) arg;
          assertThat(event.getDto().getContent()).isEqualTo(TEXT_CONTENT);
          assertThat(event.getDto().getSearchResponse().getSearchResponseId()).isEqualTo(messageDto.getSearchResponses().getFirst().getSearchResponseId());
          return true;
        }));
      });

    await().atMost(5, TimeUnit.SECONDS)
      .pollInterval(100, TimeUnit.MILLISECONDS)
      .untilAsserted(() -> {
        verify(searchQueryMessageService, atLeastOnce()).createQueryOutboxSuccessMessage(argThat(arg -> {

          assertThat(arg.getAnalysis()).isEqualTo(analysis);
          return true;
        }));
      });
  }

  private MessageUserQueryDto createMessageDto() {
    var dto = new MessageUserQueryDto();
    dto.setSearchQueryId(UUID.randomUUID());
    dto.setQueryText("query text");
    dto.setUserId(UUID.randomUUID());

    var response = new MessageUserQueryDto.SearchResponse();
    response.setSearchResponseId(UUID.randomUUID());
    response.setUrl("url");
    response.setDomain("domain");
    dto.setSearchResponses(List.of(response));
    return dto;
  }
}
