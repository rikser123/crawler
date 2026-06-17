package rikser123.crawler.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rikser123.crawler.dto.SearchResponseDtoWithContent;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FinishDownloadContentEvent {
  private List<SearchResponseDtoWithContent> context;
}
