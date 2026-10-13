package rikser123.crawler.dto.customPrompt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rikser123.crawler.repository.entity.CustomPromptMode;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CustomPromptResponseDto {
  private UUID id;
  private String name;
  private String text;
  private CustomPromptMode mode;
}
