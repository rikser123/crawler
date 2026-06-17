package rikser123.crawler.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rikser123.bundle.validation.CheckSqlInjection;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageSearchResponseDto {
  @NotNull(message = "Параметр searchResponseId должен быть заполнен!")
  private UUID searchResponseId;

  @NotNull(message = "Параметр userId должен быть заполнен!")
  private UUID userId;

  @NotEmpty(message = "Параметр url должен быть заполнен!")
  @CheckSqlInjection
  private String url;

  @NotEmpty(message = "Параметр domain должен быть заполнен!")
  @CheckSqlInjection
  private String domain;
}