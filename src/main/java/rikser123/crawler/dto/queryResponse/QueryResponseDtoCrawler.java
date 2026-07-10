package rikser123.crawler.dto.queryResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QueryResponseDtoCrawler {
  private QueryResponseDto searchResponse;
  private int attempt;
}
