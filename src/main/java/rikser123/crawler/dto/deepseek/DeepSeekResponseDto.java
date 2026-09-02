package rikser123.crawler.dto.deepseek;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@AllArgsConstructor
@NoArgsConstructor
public class DeepSeekResponseDto {
  private List<Choice> choices;
  private Error error;

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Choice {
    private Message message;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Message {
    private String content;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  public static class Error {
    private String message;
  }

  public String getContent() {
    if (choices != null && !choices.isEmpty()) {
      return choices.get(0).getMessage().getContent();
    }
    return null;
  }

  public String getErrorMessage() {
    return error != null ? error.getMessage() : null;
  }
}