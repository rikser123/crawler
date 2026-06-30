package rikser123.crawler.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rikser123.bundle.validation.CheckSqlInjection;

import java.util.List;
import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageUserQueryDto {
  @NotNull(message = "Параметр searchQueryId должен быть заполнен!")
  private UUID searchQueryId;

  @NotNull(message = "Параметр queryId должен быть заполнен!")
  private UUID queryText;

  @NotNull(message = "Параметр userId должен быть заполнен!")
  private UUID userId;

  private List<@Valid SearchResponse> searchResponses;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class SearchResponse {
    @NotNull(message = "Параметр searchResponseId должен быть заполнен!")
    private UUID searchResponseId;

    @NotEmpty(message = "Параметр url должен быть заполнен!")
    @CheckSqlInjection
    private String url;

    @NotEmpty(message = "Параметр domain должен быть заполнен!")
    @CheckSqlInjection
    private String domain;
  }
}