package rikser123.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserQueryOutboxDto {
  private UUID searchQueryId;
  private UUID userId;
  private String analysis;
  private UserQueryStatus status;
  private MessageError error;
}
