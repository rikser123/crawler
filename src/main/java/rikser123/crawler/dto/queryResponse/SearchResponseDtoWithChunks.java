package rikser123.crawler.dto.queryResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResponseDtoWithChunks {
  private QueryResponseDto searchResponse;
  private List<String> chunks;
  private int attempt;
}
