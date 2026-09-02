package rikser123.crawler.config;

import feign.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class DeepSeekFeignConfig {
  @Value("${deepseek.timeout}")
  private int timeout;

  @Bean
  public Request.Options deepSeekOptions() {
    return new Request.Options(
      Duration.ofSeconds(5),
      Duration.ofSeconds(timeout),
      false
    );
  }
}
