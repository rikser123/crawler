package rikser123.crawler.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseExtractor;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.exception.BigSizeContentException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@RequiredArgsConstructor
public class CrawlerResponseExtractor implements ResponseExtractor<String> {
  private final FetchConfigProperties fetchConfigProperties;

  @Override
  public String extractData(ClientHttpResponse response) throws IOException {
    var body = response.getBody();
    var maxSizeInBytes = fetchConfigProperties.getMaxBodySize();
    var content = new StringBuilder();


    try (var reader = new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
        var totalSize = 0;
        char[] buffer = new char[8192];
        int charsRead;

      while ((charsRead = reader.read(buffer)) != -1) {
        totalSize += charsRead * 3;

        if (totalSize >= maxSizeInBytes) {
          throw new BigSizeContentException("Размер скаченного контента превышает "  + maxSizeInBytes);
        }
        content.append(buffer, 0, charsRead);
      }

      return content.toString();
    }
  }
}
