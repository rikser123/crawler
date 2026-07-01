package rikser123.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResponseDtoWithChunks {
  private SearchResponseDto searchResponse;
  private List<String> chunks;
  private int attempt;
}
