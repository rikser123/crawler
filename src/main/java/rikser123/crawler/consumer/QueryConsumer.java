package rikser123.crawler.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import rikser123.bundle.component.ConstraintValidator;
import rikser123.crawler.service.PipelineOrchestrator;
import rikser123.crawler.dto.MessageSearchResponseDto;
import rikser123.crawler.service.SearchResponseMessageService;

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
  private final SearchResponseMessageService searchResponseMessageService;

  @KafkaListener(topics = { QUERY_TOPIC }, groupId = "crawler")
  public void requestListener(String message) {
    UUID requestResultId = null;

    try {
      var data = objectMapper.readValue(message, MessageSearchResponseDto.class);
      requestResultId = data.getSearchResponseId();
      validator.validate(data);

      pipelineOrchestrator.initResponseProcessing(data);
    } catch (Exception e) {
      if (!Objects.isNull((requestResultId))) {
        var requestOutboxMessage = searchResponseMessageService.createOutboxRequestError(
          requestResultId, e.getMessage()
        );

        searchResponseMessageService.save(requestOutboxMessage);
      }

      log.warn("error handling request result", e);
    }
  }
}
