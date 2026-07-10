package rikser123.crawler.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import rikser123.bundle.component.ConstraintValidator;
import rikser123.crawler.dto.userQuery.MessageUserQueryDto;
import rikser123.crawler.dto.userQuery.UserQueryAnalysisDto;
import rikser123.crawler.service.PipelineOrchestrator;
import rikser123.crawler.service.SearchQueryMessageService;

import java.util.Objects;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueryConsumer {
  private static final String QUERY_TOPIC = "QUERY";

  private final ObjectMapper objectMapper;
  private final ConstraintValidator validator;
  private final PipelineOrchestrator pipelineOrchestrator;
  private final SearchQueryMessageService searchQueryMessageService;

  @KafkaListener(topics = { QUERY_TOPIC }, groupId = "crawler")
  public void requestListener(String message) {
    UUID searchQueryId = null;
    UUID userId = null;

    try {
      var data = objectMapper.readValue(message, MessageUserQueryDto.class);
      searchQueryId = data.getSearchQueryId();
      userId = data.getUserId();
      validator.validate(data);

      pipelineOrchestrator.initResponseProcessing(data);
    } catch (Exception e) {
      if (!Objects.isNull((searchQueryId))) {

        var analysisDto = new UserQueryAnalysisDto();
        analysisDto.setSearchQueryId(searchQueryId);
        analysisDto.setUserId(userId);
        var queryMessage = searchQueryMessageService.createQueryOutboxErrorMessage(
          analysisDto,
          "Запрос пользователя передан с неверными параметрами"
        );

        searchQueryMessageService.save(queryMessage);
      }

      log.warn("error handling user query", e);
    }
  }
}
