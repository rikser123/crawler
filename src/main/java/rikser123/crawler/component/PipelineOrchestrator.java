package rikser123.crawler.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import rikser123.crawler.dto.MessageSearchResponseDto;
import rikser123.crawler.dto.event.FinishCleanContentEvent;
import rikser123.crawler.dto.event.FinishDownloadContentEvent;
import rikser123.crawler.dto.event.ResponseProcessingErrorEvent;
import rikser123.crawler.service.SearchResponseMessageService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class PipelineOrchestrator {
  private static final Map<UUID, MessageSearchResponseDto> searchResponsesInProcessing = new HashMap<>();

  private final Crawler crawler;
  private final TextExtractor textExtractor;
  private final SearchResponseMessageService searchResponseMessageService;


  public void initResponseProcessing(MessageSearchResponseDto responseDto) {
    var isSameUrlInProcessing = searchResponsesInProcessing.values().stream().anyMatch(response -> response.getUrl().equals(responseDto.getUrl()));
    if (!isSameUrlInProcessing) {
      crawler.initDownloading(responseDto);
    }
    searchResponsesInProcessing.put(responseDto.getSearchResponseId(), responseDto);
  }

  @EventListener
  void finishDownloadContentListener(FinishDownloadContentEvent event) {
    textExtractor.initExtraction(event.getContext());
  }

  @EventListener
  void finisCleanContentListener(FinishCleanContentEvent event) {

  }

  @EventListener
  void processingErrorListener(ResponseProcessingErrorEvent event) {
    var errorMessage = event.getMessage();
    var url = event.getUrl();
    var sameProcessingResponse = searchResponsesInProcessing
      .values()
      .stream()
      .filter(response -> response.getUrl().equals(url))
      .map(MessageSearchResponseDto::getSearchResponseId)
      .peek(responseId -> {
        searchResponsesInProcessing.remove(responseId);
      }).map(responseId -> searchResponseMessageService.createOutboxRequestError(responseId, errorMessage))
      .toList();

    searchResponseMessageService.saveAll(sameProcessingResponse);
  }
}
