package rikser123.crawler.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {
  public static final String QUERY_RESULT_TOPIC = "QUERY_RESULT";

  @Bean
  public NewTopic request() {
    return new NewTopic(QUERY_RESULT_TOPIC, 3, (short) 2);
  }
}

