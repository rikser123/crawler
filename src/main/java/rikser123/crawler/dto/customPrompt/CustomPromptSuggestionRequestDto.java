package rikser123.crawler.dto.customPrompt;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rikser123.crawler.repository.entity.CustomPromptMode;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Параметры для запроса корректировок пользовательского промпта")
public class CustomPromptSuggestionRequestDto {
  @NotEmpty(message = "Параметр prompt обязателен!")
  @Schema(description = "Текст промпта", example = "Промпт")
  private String prompt;

  @NotNull(message = "Параметр mode должен присутсовать!")
  @Schema(description = "Режим промпта", example = "SHORT")
  private CustomPromptMode mode;
}
