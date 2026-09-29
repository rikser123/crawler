package rikser123.crawler.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.support.RetrySynchronizationManager;

@Configuration
@EnableRetry
public class RetryConfig {

  @PostConstruct
  void init() {
    RetrySynchronizationManager.setUseThreadLocal(false);
  }
}
