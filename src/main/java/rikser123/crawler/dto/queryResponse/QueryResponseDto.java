package rikser123.crawler.dto.queryResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QueryResponseDto {
  private UUID searchResponseId;
  private String url;
  private String domain;
  private UUID queryId;
  private String queryText;
}