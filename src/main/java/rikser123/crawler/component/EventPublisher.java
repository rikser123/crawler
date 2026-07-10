package rikser123.crawler.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import rikser123.crawler.dto.queryResponse.QueryResponseDto;
import rikser123.crawler.dto.event.ResponseProcessingErrorEvent;

import javax.annotation.Nullable;

@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {
  private final ApplicationEventPublisher eventPublisher;

  public void publishEvent(Object event) {
    eventPublisher.publishEvent(event);
  }

  public void publishResponseProcessingErrorEvent(
    QueryResponseDto searchResponse,
    @Nullable String errorMessage
  ) {
    var errorEvent = new ResponseProcessingErrorEvent();
    errorEvent.setSearchResponseId(searchResponse.getSearchResponseId());
    errorEvent.setUrl(searchResponse.getUrl());
    errorEvent.setMessage(errorMessage);
    eventPublisher.publishEvent(errorEvent);
  }
}
