package rikser123.crawler.dto.userQuery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserQueryInitialTimeDto {
  private UserQueryDto dto;
  private Instant startTime;
}
