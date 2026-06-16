package rikser123.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KafkaMessageRequestResultStatusDto {
  private UUID requestResultId;
  private KafkaMessageRequestStatus status;
  private KafkaMessageError error;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class KafkaMessageError {
    private String message;
    private String code;
  }
}
