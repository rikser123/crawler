package rikser123.crawler.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import rikser123.bundle.component.ConstraintValidator;
import rikser123.crawler.dto.KafkaMessageRequestResultDto;
import rikser123.crawler.service.Crawler;


@Component
@RequiredArgsConstructor
@Slf4j
public class RequestConsumer {
  private static final String REQUEST_TOPIC = "REQUEST";

  private final ObjectMapper objectMapper;
  private final ConstraintValidator validator;
  private final Crawler crawler;

  @KafkaListener(topics = { REQUEST_TOPIC }, groupId = "crawler")
  public void requestListener(String message) {
    try {
      var data = objectMapper.readValue(message, KafkaMessageRequestResultDto.class);
      validator.validate(data);

      crawler.initDownloading(data);
    } catch (Exception e) {
      log.warn("ex", e);
    }
  }
}
