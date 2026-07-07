package rikser123.crawler.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import rikser123.crawler.component.EventPublisher;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.BothubRequestDto;
import rikser123.crawler.dto.DelayedSearchResponseDtoWithChunks;
import rikser123.crawler.dto.SearchResponseDto;
import rikser123.crawler.dto.SearchResponseDtoWithChunks;
import rikser123.crawler.dto.SearchResponseDtoWithContent;
import rikser123.crawler.dto.event.SummaryEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class Summariser implements PipelineStep<SearchResponseDtoWithChunks> {
  private static final int CHUNKS_COUNT = 2;

  private final BlockingQueue<SearchResponseDtoWithChunks> queue = new LinkedBlockingQueue<>();
  private final DelayQueue<DelayedSearchResponseDtoWithChunks> delayQueue = new DelayQueue<>();
  private Semaphore queueSemaphore;
  private Semaphore delayQueueSemaphore;
  private final ExecutorService executors = Executors.newVirtualThreadPerTaskExecutor();

  private final EventPublisher eventPublisher;
  private final FetchConfigProperties fetchProperties;
  private final BothubService bothubService;

  @PostConstruct
  void init() {
    queueSemaphore = new Semaphore(fetchProperties.getQueueLimit());
    delayQueueSemaphore = new Semaphore(fetchProperties.getTimeoutQueueLimit());

    initThreadPool(queue, queueSemaphore);
    initThreadPool(delayQueue, delayQueueSemaphore);
  }

  @PreDestroy
  void shutdown() {
    log.info("Shutting down Summariser...");
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
  public void initProcessing(SearchResponseDtoWithChunks searchResponseDtoWithChunks) {
    queue.add(searchResponseDtoWithChunks);
  }

  private  <T extends SearchResponseDtoWithChunks>void summarise(
    T searchResponseDtoWithChunks,
    Semaphore semaphore) {
    var acquired = false;

    try {
      semaphore.acquire();
      acquired = true;
      var relevantChunks = getRelevantChunks(searchResponseDtoWithChunks);
      var summary = bothubService.getSummary(relevantChunks);
      publishSummaryEvent(searchResponseDtoWithChunks.getSearchResponse(), summary);
    } catch (IllegalStateException e) {
      var delay = fetchProperties.getRepeatDownloadDelay();
      var maxAttempt = fetchProperties.getMaxDownloadAttempt();

      var attempt = searchResponseDtoWithChunks.getAttempt() + 1;
      if (attempt >= maxAttempt) {
        eventPublisher.publishResponseProcessingErrorEvent(
          searchResponseDtoWithChunks.getSearchResponse(),
          "Не удалось определить релевантные чанки"
        );
        return;
      }

      var delayDto = new DelayedSearchResponseDtoWithChunks();
      delayDto.setDelayInSeconds(delay);
      delayDto.setSearchResponse(searchResponseDtoWithChunks.getSearchResponse());
      delayDto.setChunks(searchResponseDtoWithChunks.getChunks());
      delayDto.setAttempt(attempt);
      delayQueue.add(delayDto);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      if (acquired) {
        semaphore.release();
      }
    }
  }

  private List<String> getRelevantChunks(SearchResponseDtoWithChunks searchResponseDto) {
    var chunks = searchResponseDto.getChunks();
    var queryText = searchResponseDto.getSearchResponse().getQueryText();

    if (chunks.size() <= 2) {
      return chunks;
    }

    var relevantChunks = new ArrayList<String>();
    var analyzer = new StandardAnalyzer();
    var config = new IndexWriterConfig(analyzer);

    try (var directory = new ByteBuffersDirectory();
         var writer = new IndexWriter(directory, config)
    ) {
      for (int i = 0; i < chunks.size(); i++) {
        var doc = new Document();
        doc.add(new TextField("content", chunks.get(i), Field.Store.YES));
        doc.add(new TextField("id", String.valueOf(i), Field.Store.YES));
        writer.addDocument(doc);
      }
      writer.commit();

      try (var reader = DirectoryReader.open(directory)) {
        var searcher = new IndexSearcher(reader);
        var parser = new QueryParser("content", analyzer);
        var query = parser.parse(queryText);
        var hits = searcher.search(query, CHUNKS_COUNT).scoreDocs;

        if (hits.length == 0) {
          throw new IllegalStateException("Не удалось определить релевантные чанки");
        }

        for (var hit : hits) {
          var doc = searcher.doc(hit.doc);
          var content = doc.get("content");
          relevantChunks.add(content);
        }
      }

      return relevantChunks;

    } catch (Exception e) {
      log.warn("Не удалось определить релевантные чанки", e);
      throw new IllegalStateException("Не удалось определить релевантные чанки");
    }
  }

  private void publishSummaryEvent(SearchResponseDto searchResponseDto, String summary) {
    var event = new SummaryEvent();
    var eventDto = new SearchResponseDtoWithContent();
    eventDto.setSearchResponse(searchResponseDto);
    eventDto.setContent(summary);
    event.setSearchDto(eventDto);
    eventPublisher.publishEvent(event);
  }

  private <T extends SearchResponseDtoWithChunks>void initThreadPool(BlockingQueue<T> queue, Semaphore semaphore) {
    executors.execute(() -> {
      while (true) {
        try {
          var request = queue.take();
          executors.execute(() -> summarise(request, semaphore));
        }  catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
    });
  }
}
