package rikser123.crawler.dto.userQuery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QueryAnalysisDto {
  private UUID userId;
  private UUID searchQueryId;
  private String queryText;
  private List<String> texts;
}
