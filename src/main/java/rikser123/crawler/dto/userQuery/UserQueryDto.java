package rikser123.crawler.dto.userQuery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserQueryDto {
  private UUID searchQueryId;
  private UUID userId;
  private String queryText;
  private List<SearchResponseDtoWithContent> searchResponses;
}
