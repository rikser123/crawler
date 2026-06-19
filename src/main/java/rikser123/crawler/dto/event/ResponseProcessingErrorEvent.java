package rikser123.crawler.dto.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ResponseProcessingErrorEvent {
  private String code;
  private String message;
  private UUID searchResponseId;
  private String url;
}
