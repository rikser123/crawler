package rikser123.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProcessedRequestDto {
  private KafkaMessageRequestResultDto request;
  private int attempt;
}
