package rikser123.crawler.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;
import rikser123.bundle.repository.entity.OutboxMessageStatus;
import rikser123.crawler.repository.entity.SearchQueryOutboxMessage;

import rikser123.crawler.service.SearchQueryMessageService;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import static rikser123.crawler.config.KafkaTopicConfig.QUERY_ANALYSIS_TOPIC;

@Component
@RequiredArgsConstructor
@Slf4j
public class QueryAnalysisProducer {
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;
  private final SearchQueryMessageService searchQueryMessageService;

  @SneakyThrows
  public CompletableFuture<SendResult<String, String>> send(SearchQueryOutboxMessage kafkaMessage) {
    var dto = kafkaMessage.getDto();
    var message = objectMapper.writeValueAsString(dto);

    return kafkaTemplate.send(QUERY_ANALYSIS_TOPIC, message).whenComplete((result, error) -> {
      if (!Objects.isNull(result)) {
        log.info("message successfully send {} in {}", kafkaMessage.getId(), QUERY_ANALYSIS_TOPIC);
        searchQueryMessageService.changeStatus(kafkaMessage, OutboxMessageStatus.SENT);
      } else if (!Objects.isNull(error)) {
        log.warn("message fail send {} in {}", kafkaMessage.getId(), QUERY_ANALYSIS_TOPIC);
      }
    });
  }
}
