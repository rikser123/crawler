package rikser123.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserQueryDto {
  private UUID searchQueryId;
  private UUID userId;
  private List<SearchResponseDto> searchResponses;
}
