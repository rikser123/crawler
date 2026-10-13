package rikser123.crawler.dto.customPrompt;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rikser123.crawler.repository.entity.CustomPromptMode;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Schema(description = "Параметры для создания и редактирования пользовательского промпта")
public class CustomPromptRequestDto {
  @Size(message = "Длина не должна превышать 2000 символов!", max = 2000)
  @NotEmpty(message = "Параметр text обязателен!")
  @Schema(description = "Текст промпта", example = "Промпт")
  private String text;

  @Size(message = "Длина не должна превышать 200 символов!", max = 200)
  @NotEmpty(message = "Параметр name обязателен!")
  @Schema(description = "Текст промпта", example = "Промпт")
  private String name;

  @NotNull(message = "Параметр mode должен присутсовать!")
  @Schema(description = "Режим промпта", example = "SHORT")
  private CustomPromptMode mode;

  @Schema(description = "Id промпта")
  private UUID id;
}
