package rikser123.crawler.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.apache.tika.sax.boilerpipe.BoilerpipeContentHandler;
import org.apache.tika.parser.html.JSoupParser;
import org.springframework.stereotype.Service;
import rikser123.crawler.component.PrometheusMetrics;
import rikser123.crawler.dto.queryResponse.SearchResponseDtoWithContent;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

@Service
@Slf4j
@RequiredArgsConstructor
public class TextExtractor {
  private static final Integer CONTENT_LENGTH_LIMIT = 1_000_000;

  private final PrometheusMetrics prometheusMetrics;

  public SearchResponseDtoWithContent extractText(SearchResponseDtoWithContent searchResponse) {
    var textHandler = new BodyContentHandler(CONTENT_LENGTH_LIMIT);
    var handler = new BoilerpipeContentHandler(textHandler);

    var parser = new JSoupParser();
    var metadata = new Metadata();
    var context = new ParseContext();

    try(var stream = new ByteArrayInputStream(searchResponse.getContent().getBytes(StandardCharsets.UTF_8))) {
      parser.parse(stream, handler, metadata, context);

      prometheusMetrics.incrementCleanContent();

      var searchDto = new SearchResponseDtoWithContent();
      searchDto.setSearchResponse(searchResponse.getSearchResponse());
      searchDto.setContent(textHandler.toString());
      return searchDto;

    } catch (Exception e) {
       log.warn("Error during clean html", e);
       throw new IllegalStateException( "Не удалось извлечь текст из контента");
    }
  }
}
