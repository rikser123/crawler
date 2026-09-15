package rikser123.crawler.dto.llm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LlmRequestDto {
  private String model;
  private List<Message> messages;
  private final Boolean stream = false;
  private Double temperature = 0.7;
  private Integer maxTokens;
  private Boolean enableThinking = false;
  private ResponseFormat responseFormat;

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Message {
    private String role = "user";
    private String content;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class ResponseFormat {
    private String type = "json_object";
  }
}
