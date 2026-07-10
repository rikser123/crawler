package rikser123.crawler.service;

import org.springframework.stereotype.Service;
import rikser123.bundle.repository.entity.OutboxMessageStatus;
import rikser123.bundle.service.OutboxMessageService;
import rikser123.bundle.service.StatusMatrix;
import rikser123.crawler.dto.MessageError;
import rikser123.crawler.dto.userQuery.UserQueryAnalysisDto;
import rikser123.crawler.dto.userQuery.UserQueryOutboxDto;
import rikser123.crawler.dto.userQuery.UserQueryStatus;
import rikser123.crawler.repository.SearchQueryOutboxMessageRepository;
import rikser123.crawler.repository.entity.SearchQueryOutboxMessage;

@Service
public class SearchQueryMessageService extends OutboxMessageService<SearchQueryOutboxMessage> {
  public SearchQueryMessageService(
    SearchQueryOutboxMessageRepository searchQueryOutboxMessageRepository,
    StatusMatrix<OutboxMessageStatus> outboxStatusMatrix)
  {
    super(searchQueryOutboxMessageRepository, outboxStatusMatrix);
  }

  public SearchQueryOutboxMessage createQueryOutboxErrorMessage(UserQueryAnalysisDto dto, String message) {
    var messageDto = createOutboxDto(dto, UserQueryStatus.FAILED);

    var error = new MessageError();
    error.setMessage(message);
    messageDto.setError(error);

    var requestOutboxMessage = createOutboxMessage(messageDto);

    return requestOutboxMessage;
  }

  public SearchQueryOutboxMessage createQueryOutboxSuccessMessage(UserQueryAnalysisDto dto) {
    var messageDto = createOutboxDto(dto, UserQueryStatus.PROCESSED);
    messageDto.setAnalysis(dto.getAnalysis());
    var requestOutboxMessage = createOutboxMessage(messageDto);

    return requestOutboxMessage;
  }

  private UserQueryOutboxDto createOutboxDto(UserQueryAnalysisDto dto, UserQueryStatus status) {
    var messageDto = new UserQueryOutboxDto();
    messageDto.setSearchQueryId(dto.getSearchQueryId());
    messageDto.setUserId(dto.getUserId());
    messageDto.setStatus(status);
    return messageDto;
  }

  private SearchQueryOutboxMessage createOutboxMessage(UserQueryOutboxDto messageDto) {
    var requestOutboxMessage = new SearchQueryOutboxMessage();
    requestOutboxMessage.setDto(messageDto);
    requestOutboxMessage.setStatus(OutboxMessageStatus.CREATED);
    return requestOutboxMessage;
  }
}