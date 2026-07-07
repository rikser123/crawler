package rikser123.crawler.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KafkaTopicConfig {
  public static final String QUERY_RESULT_TOPIC = "QUERY_RESULT";
  public static final String QUERY_ANALYSIS_TOPIC = "QUERY_ANALYSIS_TOPIC";

  @Bean
  public NewTopic queryResult() {
    return new NewTopic(QUERY_RESULT_TOPIC, 3, (short) 2);
  }

  @Bean
  public NewTopic queryAnalysis() {
    return new NewTopic(QUERY_ANALYSIS_TOPIC, 3, (short) 2);
  }
}

