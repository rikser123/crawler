package rikser123.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResponseDto {
  private UUID searchResponseId;
  private String url;
  private String domain;
  private UUID queryId;
  private SearchResponseDtoStatus status;
}