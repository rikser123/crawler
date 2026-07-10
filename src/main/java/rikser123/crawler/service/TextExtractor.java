package rikser123.crawler.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.boilerpipe.BoilerpipeContentHandler;
import org.apache.tika.parser.html.JSoupParser;
import org.springframework.stereotype.Service;
import rikser123.crawler.component.EventPublisher;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;
import rikser123.crawler.dto.event.FinishCleanContentEvent;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class TextExtractor implements PipelineStep<SearchResponseDtoWithContent> {
  private static final Integer CONTENT_LENGTH_LIMIT = 1_000_000;

  private final BlockingQueue<SearchResponseDtoWithContent> queue = new LinkedBlockingQueue<>();
  private final ExecutorService executors = Executors.newVirtualThreadPerTaskExecutor();

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
          break;
        }
      }
    });
  }

  @PreDestroy
  void shutdown() {
    log.info("Shutting down TextExtractor...");
    executors.shutdown();
    try {
      if (!executors.awaitTermination(30, TimeUnit.SECONDS)) {
        executors.shutdownNow();
      }
    } catch (InterruptedException e) {
      executors.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  @Override
  public void initProcessing(SearchResponseDtoWithContent responseDto) {
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
      finishEvent.setDto(searchDto);
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
