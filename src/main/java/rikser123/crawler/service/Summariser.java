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
import rikser123.crawler.feign.BothubClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
public class Summariser {
  private static final BlockingQueue<SearchResponseDtoWithChunks> queue = new LinkedBlockingQueue<>();
  private static final DelayQueue<DelayedSearchResponseDtoWithChunks> delayQueue = new DelayQueue<>();
  private static Semaphore queueSemaphore;
  private static Semaphore delayQueueSemaphore;
  private static final ExecutorService executors = Executors.newVirtualThreadPerTaskExecutor();

  private static final int CHUNKS_COUNT = 2;
  private static final String SUMMARY_MODEL = "deepseek-v4-flash";

  private final EventPublisher eventPublisher;
  private final BothubClient bothubClient;
  private final FetchConfigProperties fetchProperties;

  @Value("${bothub.token}")
  private String bothubToken;

  @PostConstruct
  void init() {
    queueSemaphore = new Semaphore(fetchProperties.getQueueLimit());
    delayQueueSemaphore = new Semaphore(fetchProperties.getTimeoutQueueLimit());

    initThreadPool(queue, queueSemaphore);
    initThreadPool(delayQueue, delayQueueSemaphore);
  }

  @PreDestroy
  public void shutdown() {
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

  public void initSummarising(SearchResponseDtoWithChunks searchResponseDtoWithChunks) {
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
      var summary = getSummary(relevantChunks);
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

  private String getSummary(List<String> chunks) {
    var requestDto = new BothubRequestDto();
    requestDto.setModel(SUMMARY_MODEL);

    var prompt = String.format("""
      Инструкция: Ты — профессиональный анализатор текста. Составь краткий структурированный конспект статьи для дальнейшего использования в аналитической системе.
      Задача: Извлеки из текста всю значимую информацию и представь её в виде краткого, фактологического конспекта. Конспект должен быть максимально информативным, но при этом занимать не более 500–700 слов.
      Структура конспекта (строго соблюдай):
      Основная тема: 1 предложение, формулирующее главную тему статьи.
      Ключевые тезисы: список из 4–7 пунктов, содержащих самые важные утверждения, факты, цифры, выводы. Каждый пункт — 1–2 предложения. Используй маркированный список.
      Детали: (если есть) важные нюансы, уточнения, исключения, которые дополняют основные тезисы. Кратко, 2–3 предложения.
      Источники и данные: если в статье есть ссылки на исследования, даты, имена, статистика — обязательно укажи.
      Важно: Пиши только по тексту. Никаких своих оценок, обобщений или домыслов. Стиль — нейтральный, деловой, без воды.
      Текст статьи:
      %s
      """, String.join(", ", chunks));
    requestDto.setInput(prompt);
    var response = bothubClient.getResponses(requestDto, "Bearer " + bothubToken);
    if (!Objects.isNull(response.getError())) {
      log.warn("Не удалось получить ответ от {}", SUMMARY_MODEL);
      throw new IllegalStateException("Не удалось определить релевантные чанки");
    }
    return response.getOutputText();
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
