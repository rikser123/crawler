package rikser123.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KafkaMessageRequestResultDto {
  private UUID requestResultId;
  private UUID userId;
  private String url;
  private String domain;
}