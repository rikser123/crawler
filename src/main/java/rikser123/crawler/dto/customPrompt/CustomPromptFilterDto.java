package rikser123.crawler.dto.customPrompt;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rikser123.crawler.repository.entity.CustomPromptMode;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Параметры для фильтрации пользовательских промптов")
public class CustomPromptFilterDto {
  @Schema(description = "Режим пользовательского промпта", example = "SHORT")
  private CustomPromptMode mode;
}
