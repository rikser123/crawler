package rikser123.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DelayedSearchResponseDtoWithChunks extends SearchResponseDtoWithChunks implements Delayed {
  private int delayInSeconds;
  private long startTime = System.currentTimeMillis();

  @Override
  public long getDelay(TimeUnit unit) {
    var current = System.currentTimeMillis();
    var diff = current - startTime;
    var remaining = delayInSeconds * 1000 - diff;
    return unit.convert(remaining, TimeUnit.MILLISECONDS);

  }

  @Override
  public int compareTo(Delayed o) {
    return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
  }
}
