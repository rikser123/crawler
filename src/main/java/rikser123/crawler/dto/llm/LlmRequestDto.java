package rikser123.crawler.dto.llm;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LlmRequestDto {
  private String model;
  private List<Message> messages;
  private final Boolean stream = false;
  private Double temperature = 0.7;

  @JsonProperty("max_tokens")
  private Integer maxTokens;

  @JsonProperty("enable_thinking")
  @Builder.Default
  private Boolean enableThinking = false;

  @JsonProperty("reasoning_effort")
  private String reasoningEffort;

  @JsonProperty("response_format")
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
