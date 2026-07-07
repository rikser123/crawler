package rikser123.crawler.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class MessageQueryResponseDtoOutbox {
  private UUID searchResponseId;
  private SearchResponseStatus status;
  private MessageError error;
}
