package rikser123.crawler.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import rikser123.bundle.repository.entity.OutboxMessageStatus;
import rikser123.crawler.repository.entity.RequestResultOutboxMessage;
import rikser123.crawler.service.RequestResultOutboxMessageService;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static rikser123.crawler.config.KafkaTopicConfig.REQUEST_RESULT_TOPIC;

@Component
@RequiredArgsConstructor
@Slf4j
public class RequestResultStatusProducer {
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final RequestResultOutboxMessageService requestResultOutboxMessageService;

  @SneakyThrows
  public CompletableFuture<SendResult<String, String>> send(RequestResultOutboxMessage kafkaRequestMessage) {
    var dto = kafkaRequestMessage.getDto();
    var message = objectMapper.writeValueAsString(dto);

    return kafkaTemplate.send(REQUEST_RESULT_TOPIC, message).whenComplete((result, error) -> {
      if (!Objects.isNull(result)) {
        log.info("message successfully send {}", kafkaRequestMessage.getId());
        requestResultOutboxMessageService.changeStatus(kafkaRequestMessage, OutboxMessageStatus.SENT);
      } else if (!Objects.isNull(error)) {
        log.warn("message fail send {}", kafkaRequestMessage.getId());
      }
    });
  }
}
