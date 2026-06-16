package rikser123.crawler.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import rikser123.bundle.component.ConstraintValidator;
import rikser123.crawler.dto.KafkaMessageRequestResultDto;
import rikser123.crawler.service.Crawler;
import rikser123.crawler.service.RequestResultOutboxMessageService;

import java.util.Objects;
import java.util.UUID;


@Component
@RequiredArgsConstructor
@Slf4j
public class RequestConsumer {
  private static final String REQUEST_TOPIC = "REQUEST";

  private final ObjectMapper objectMapper;
  private final ConstraintValidator validator;
  private final Crawler crawler;
  private final RequestResultOutboxMessageService requestResultOutboxMessageService;

  @KafkaListener(topics = { REQUEST_TOPIC }, groupId = "crawler")
  public void requestListener(String message) {
    UUID requestResultId = null;

    try {
      var data = objectMapper.readValue(message, KafkaMessageRequestResultDto.class);
      requestResultId = data.getRequestResultId();
      validator.validate(data);

      crawler.initDownloading(data);
    } catch (Exception e) {
      if (!Objects.isNull((requestResultId))) {
        var requestOutboxMessage = requestResultOutboxMessageService.createOutboxRequestError(
          requestResultId, e.getMessage()
        );

        requestResultOutboxMessageService.save(requestOutboxMessage);
      }

      log.warn("error handling request result", e);
    }
  }
}
