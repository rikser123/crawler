package rikser123.crawler.dto.llm;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LlmRequestDto {
  private String model;
  private List<Message> messages;
  private final Boolean stream = false;
  private final Double temperature = 0.7;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Message {
    private String role = "user";
    private String content;
  }
}
