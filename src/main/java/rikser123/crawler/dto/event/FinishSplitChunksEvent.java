package rikser123.crawler.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithChunks;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FinishSplitChunksEvent {
  private SearchResponseDtoWithChunks dto;
}
