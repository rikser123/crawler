package rikser123.crawler.dto.queryResponse;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import rikser123.crawler.dto.MessageError;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageQueryResponseDtoOutbox {
  private UUID searchResponseId;
  private QueryResponseStatus status;
  private MessageError error;
}
