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
  private Counter userSearchQuery;
  private Counter queryResponse;
  private Counter finishDownload;
  private Counter cleanContent;
  private Counter splitChunks;
  private Counter summary;
  private Counter querySuccess;
  private Counter queryFail;
  private Counter failResponse;

  @PostConstruct
  void init() {
    userSearchQuery = Counter.builder("query.total")
      .description("Total number of user query")
      .register(meterRegistry);
    querySuccess = Counter.builder("query.success")
      .description("Total of success query")
      .register(meterRegistry);
    queryFail = Counter.builder("query.fail")
      .description("Total of fail query")
      .register(meterRegistry);

    queryResponse = Counter.builder("searchQueryResponse.total")
      .description("Total number of search query response")
      .register(meterRegistry);
    finishDownload = Counter.builder("searchQueryResponse.download")
      .description("Number of downloaded requests")
      .register(meterRegistry);
    cleanContent = Counter.builder("searchQueryResponse.cleanContent")
      .description("Number of clean content requests")
      .register(meterRegistry);
    splitChunks = Counter.builder("searchQueryResponse.splitChunks")
      .description("Number of split chunks requests")
      .register(meterRegistry);
    summary = Counter.builder("searchQueryResponse.summary")
      .description("Number of summary requests")
      .register(meterRegistry);
    failResponse = Counter.builder("searchQueryResponse.fail")
      .description("Number of fail requests")
      .register(meterRegistry);
  }

  public void incrementSearchQuery() {
    userSearchQuery.increment();
  }

  public void incrementQueryResponse() {
    queryResponse.increment();
  }

  public void incrementFinishDownload() {
    finishDownload.increment();
  }

  public void incrementCleanContent() {
    cleanContent.increment();
  }

  public void incrementSplitChunks() {
    splitChunks.increment();
  }

  public void incrementSummary() {
    summary.increment();
  }

  public void incrementSuccessQuery() {
    querySuccess.increment();
  }

  public void incrementFailQuery() {
    queryFail.increment();
  }

  public void incrementFailResponse() {
    failResponse.increment();
  }
}

