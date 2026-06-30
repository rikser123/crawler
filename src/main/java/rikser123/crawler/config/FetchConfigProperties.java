package rikser123.crawler.config;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@ConfigurationProperties(prefix = "fetch")
@Component
@Data
@AllArgsConstructor
@NoArgsConstructor
public class FetchConfigProperties {
  private int queueLimit;
  private int timeoutQueueLimit;
  private int maxDownloadAttempt;
  private int repeatDownloadDelay;
  private int maxBodySize;
  private int chunkSize;
  private int wordOverlapCount;
}
