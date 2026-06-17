package rikser123.crawler.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import rikser123.crawler.dto.MessageSearchResponseDto;
import rikser123.crawler.dto.event.FinishDownloadContentEvent;
import rikser123.crawler.dto.event.ResponseProcessingErrorEvent;
import rikser123.crawler.service.Crawler;
import rikser123.crawler.service.SearchResponseMessageService;

@Component
@RequiredArgsConstructor
@Slf4j
public class PipelineOrchestrator {
  private final Crawler crawler;
  private final SearchResponseMessageService searchResponseMessageService;

  public void initResponseProcessing(MessageSearchResponseDto responseDto) {
    crawler.initDownloading(responseDto);
  }

  @EventListener
  void finishDownloadContentListener(FinishDownloadContentEvent event) {

  }

  @EventListener
  void processingErrorListener(ResponseProcessingErrorEvent event) {
    var errorMessage = event.getMessage();
    var searchResponseId = event.getSearchResponseId();
    var requestOutboxMessage = searchResponseMessageService.createOutboxRequestError(
      searchResponseId, errorMessage
    );

    searchResponseMessageService.save(requestOutboxMessage);
  }
}
