package rikser123.crawler.config;

import feign.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class BothubFeignConfig {
  @Value("${bothub.timeout}")
  private int timeout;

  @Bean
  public Request.Options bothubOptions() {
    return new Request.Options(
      Duration.ofSeconds(5),
      Duration.ofSeconds(timeout),
      false
    );
  }
}
