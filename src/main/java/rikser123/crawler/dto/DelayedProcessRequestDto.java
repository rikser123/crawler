package rikser123.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DelayedProcessRequestDto extends ProcessedRequestDto implements Delayed {
  private int attempt;
  private int delayInSeconds;

  @Override
  public long getDelay(TimeUnit unit) {
    return delayInSeconds;
  }

  @Override
  public int compareTo(Delayed o) {
    return Long.compare(this.getDelay(TimeUnit.SECONDS), o.getDelay(TimeUnit.SECONDS));
  }
}
