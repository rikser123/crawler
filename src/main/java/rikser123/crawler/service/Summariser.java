package rikser123.crawler.service;

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
import org.springframework.stereotype.Service;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.dto.queryResponse.QueryResponseDto;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithChunks;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class Summariser {
  private static final int CHUNKS_COUNT = 2;

  private final FetchConfigProperties fetchProperties;
  private final DeepSeekService deepSeekService;


  public  SearchResponseDtoWithContent summarise(SearchResponseDtoWithChunks searchResponseDtoWithChunks) {
    var attempt = 0;
    var delay = 0;

    while (attempt < fetchProperties.getMaxDownloadAttempt()) {
      try {
        attempt += 1;
        var relevantChunks = getRelevantChunks(searchResponseDtoWithChunks);
        var summary = deepSeekService.getSummary(relevantChunks);
        return getSummaryDto(searchResponseDtoWithChunks.getSearchResponse(), summary);
      } catch (IllegalStateException e) {
        if (attempt >= fetchProperties.getMaxDownloadAttempt()) {
          throw new IllegalStateException("Не удалось определить релевантные чанки", e);
        }
        delay = fetchProperties.getRepeatDownloadDelay();
      } finally {
        if (delay > 0) {
          try {
            Thread.sleep(delay);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
        }
      }
    }

    throw new IllegalStateException("Не удалось определить релевантные чанки");
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

  private SearchResponseDtoWithContent getSummaryDto(QueryResponseDto queryResponseDto, String summary) {
    var dto = new SearchResponseDtoWithContent();
    dto.setSearchResponse(queryResponseDto);
    dto.setContent(summary);
    return dto;
  }
}
