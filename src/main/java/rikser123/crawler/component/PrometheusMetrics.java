package rikser123.crawler.component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PrometheusMetrics {
  private final MeterRegistry meterRegistry;
  private Counter totalRequests;
  private Counter cacheHits;
  private Counter successRequests;
  private Counter failRequests;

  @PostConstruct
  void init() {
    totalRequests = Counter.builder("requests.total")
      .description("Total number of requests")
      .register(meterRegistry);
    cacheHits = Counter.builder("requests.cache")
      .description("Number of requests from cache")
      .register(meterRegistry);
    successRequests = Counter.builder("requests.success")
      .description("Number of success requests")
      .register(meterRegistry);
    failRequests = Counter.builder("requests.fail")
      .description("Number of fail requests")
      .register(meterRegistry);
  }

  public void incrementTotal() {
    totalRequests.increment();
  }

  public void incrementCache() {
    cacheHits.increment();
  }

  public void incrementSuccess() {
    successRequests.increment();
  }

  public void incrementFail() {
    failRequests.increment();
  }
}

