package rikser123.crawler.dto.customPrompt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class CustomPromptListResponseDto {
  private List<CustomPromptResponseDto> items;
}
