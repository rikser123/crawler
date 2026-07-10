package rikser123.crawler.dto.bothub;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BothubResponseDto {
  private String id;
  private String status;
  private String model;

  @JsonProperty("output_text")
  private String outputText;
  private Object error;
}
