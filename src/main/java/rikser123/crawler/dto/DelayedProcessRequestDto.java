package rikser123.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rikser123.crawler.constants.AppConstants;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DelayedProcessRequestDto extends ProcessedRequestDto implements Delayed {
  private int attempt;

  @Override
  public long getDelay(TimeUnit unit) {
    return AppConstants.DOWNLOAD_DELAY_IN_SECONDS;
  }

  @Override
  public int compareTo(Delayed o) {
    return Long.compare(this.getDelay(TimeUnit.SECONDS), o.getDelay(TimeUnit.SECONDS));
  }
}
