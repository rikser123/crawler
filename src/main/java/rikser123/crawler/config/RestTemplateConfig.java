package rikser123.crawler.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {
  @Value("${rest.connection-timeout}")
  private int connectionTimeout;

  @Value("${rest.read-timeout}")
  private int readTimeout;

  @Bean
  public RestTemplate restTemplate(RestTemplateBuilder builder) {
    return builder
      .connectTimeout(Duration.ofSeconds(connectionTimeout))
      .readTimeout(Duration.ofSeconds(readTimeout))
      .build();
  }
}
