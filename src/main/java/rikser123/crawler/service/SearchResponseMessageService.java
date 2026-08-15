package rikser123.crawler.service;

import org.springframework.stereotype.Service;
import rikser123.bundle.repository.entity.OutboxMessageStatus;
import rikser123.bundle.service.OutboxMessageService;
import rikser123.bundle.service.StatusMatrix;
import rikser123.crawler.dto.MessageError;
import rikser123.crawler.dto.queryResponse.MessageQueryResponseDtoOutbox;
import rikser123.crawler.dto.queryResponse.QueryResponseStatus;
import rikser123.crawler.repository.SearchResponseOutboxMessageRepository;
import rikser123.crawler.repository.entity.SearchResponseOutboxMessage;

import java.util.UUID;

@Service
public class SearchResponseMessageService extends OutboxMessageService<SearchResponseOutboxMessage> {
  public SearchResponseMessageService(
    SearchResponseOutboxMessageRepository searchResponseOutboxMessageRepository,
    StatusMatrix<OutboxMessageStatus> outboxStatusMatrix)
  {
    super(searchResponseOutboxMessageRepository, outboxStatusMatrix);
  }

  public SearchResponseOutboxMessage createOutboxSuccessMessage(UUID requestResultId) {
    var messageDto = new MessageQueryResponseDtoOutbox();
    messageDto.setSearchResponseId(requestResultId);
    messageDto.setStatus(QueryResponseStatus.PROCESSED);

    return createOutboxMessage(messageDto);
  }

  public SearchResponseOutboxMessage createOutboxRequestError(UUID requestResultId, String message) {
    var messageDto = new MessageQueryResponseDtoOutbox();
    messageDto.setSearchResponseId(requestResultId);
    messageDto.setStatus(QueryResponseStatus.FAILED);

    var error = new MessageError();
    error.setMessage(message);
    messageDto.setError(error);

    return createOutboxMessage(messageDto);
  }

  private SearchResponseOutboxMessage createOutboxMessage(MessageQueryResponseDtoOutbox messageDto) {
    var requestOutboxMessage = new SearchResponseOutboxMessage();
    requestOutboxMessage.setDto(messageDto);
    requestOutboxMessage.setStatus(OutboxMessageStatus.CREATED);

    return requestOutboxMessage;
  }
}
