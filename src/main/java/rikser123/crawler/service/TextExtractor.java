package rikser123.crawler.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.boilerpipe.BoilerpipeContentHandler;
import org.apache.tika.parser.html.JSoupParser;
import org.springframework.stereotype.Service;
import rikser123.crawler.component.EventPublisher;
import rikser123.crawler.dto.SearchResponseDtoWithContent;
import rikser123.crawler.dto.event.FinishCleanContentEvent;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

@Service
@Slf4j
@RequiredArgsConstructor
public class TextExtractor {
  private static final BlockingQueue<SearchResponseDtoWithContent> queue = new LinkedBlockingQueue<>();
  private static final Executor executors = Executors.newVirtualThreadPerTaskExecutor();
  private static final Integer CONTENT_LENGTH_LIMIT = 1_000_000;

  private final EventPublisher eventPublisher;

  @PostConstruct
  void init() {
    executors.execute(() -> {
      while (true) {
        try {
          var request = queue.take();
          executors.execute(() -> extractText(request));
        }  catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      }
    });
  }

  public void initExtraction(SearchResponseDtoWithContent responseDto) {
    queue.add(responseDto);
  }

  private void extractText(SearchResponseDtoWithContent searchResponse) {
    var textHandler = new BodyContentHandler(CONTENT_LENGTH_LIMIT);
    var handler = new BoilerpipeContentHandler(textHandler);

    var parser = new JSoupParser();
    var metadata = new Metadata();
    var context = new ParseContext();

    try(var stream = new ByteArrayInputStream(searchResponse.getContent().getBytes(StandardCharsets.UTF_8))) {
      parser.parse(stream, handler, metadata, context);

      var finishEvent = new FinishCleanContentEvent();
      var searchDto = new SearchResponseDtoWithContent();
      searchDto.setSearchResponse(searchResponse.getSearchResponse());
      searchDto.setContent(textHandler.toString());
      finishEvent.setSearchResponseDto(searchDto);
      eventPublisher.publishEvent(finishEvent);

    } catch (Exception e) {
       log.warn("Error during clean html", e);
       eventPublisher.publishResponseProcessingErrorEvent(
         searchResponse.getSearchResponse(),
         "Не удалось извлечь текст из контента " +e.getMessage()
       );
    }
  }
}
