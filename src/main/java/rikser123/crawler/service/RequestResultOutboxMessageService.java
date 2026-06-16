package rikser123.crawler.service;

import org.springframework.stereotype.Service;
import rikser123.bundle.repository.entity.OutboxMessageStatus;
import rikser123.bundle.service.OutboxMessageService;
import rikser123.bundle.service.StatusMatrix;
import rikser123.crawler.dto.KafkaMessageRequestResultStatusDto;
import rikser123.crawler.dto.KafkaMessageRequestStatus;
import rikser123.crawler.repository.RequestResultOutboxMessageRepository;
import rikser123.crawler.repository.entity.RequestResultOutboxMessage;

import java.util.UUID;

@Service
public class RequestResultOutboxMessageService extends OutboxMessageService<RequestResultOutboxMessage> {
  public RequestResultOutboxMessageService(
    RequestResultOutboxMessageRepository requestResultOutboxMessageRepository,
    StatusMatrix<OutboxMessageStatus> outboxStatusMatrix)
  {
    super(requestResultOutboxMessageRepository, outboxStatusMatrix);
  }

  public RequestResultOutboxMessage createOutboxRequestError(UUID requestResultId, String message) {
    var messageDto = new KafkaMessageRequestResultStatusDto();
    messageDto.setRequestResultId(requestResultId);
    messageDto.setStatus(KafkaMessageRequestStatus.FAILED);

    var error = new KafkaMessageRequestResultStatusDto.KafkaMessageError();
    error.setMessage(message);
    messageDto.setError(error);

    var requestOutboxMessage = new RequestResultOutboxMessage();
    requestOutboxMessage.setDto(messageDto);
    requestOutboxMessage.setStatus(OutboxMessageStatus.CREATED);

    return requestOutboxMessage;
  }
}
