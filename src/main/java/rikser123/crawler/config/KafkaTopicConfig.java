package rikser123.crawler.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {
  public static final String REQUEST_RESULT_TOPIC = "REQUEST_RESULT";

  @Bean
  public NewTopic request() {
    return new NewTopic(REQUEST_RESULT_TOPIC, 3, (short) 2);
  }
}

