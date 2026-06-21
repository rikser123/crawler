package rikser123.crawler.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rikser123.crawler.dto.SearchResponseDtoWithContent;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FinishCleanContentEvent {
  private SearchResponseDtoWithContent searchResponseDto;
}
