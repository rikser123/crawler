package rikser123.crawler.dto.queryResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResponseDtoWithContent {
  private QueryResponseDto searchResponse;
  private String content;
  private QueryResponseDtoStatus status;
}
