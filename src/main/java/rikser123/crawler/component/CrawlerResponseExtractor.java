package rikser123.crawler.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResponseExtractor;
import rikser123.crawler.config.FetchConfigProperties;
import rikser123.crawler.exception.BigSizeContentException;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

@Component
@Slf4j
@RequiredArgsConstructor
public class CrawlerResponseExtractor implements ResponseExtractor<String> {
  private final FetchConfigProperties fetchConfigProperties;

  @Override
  public String extractData(ClientHttpResponse response) throws IOException {
    var maxSizeInBytes = fetchConfigProperties.getMaxBodySize();
    var contentLength = response.getHeaders().getContentLength();
    var errorMessage = "Размер скаченного контента превышает "  + maxSizeInBytes;

    if (contentLength > maxSizeInBytes) {
      log.warn(errorMessage);
      throw new BigSizeContentException(errorMessage);
    }

    byte[] bytes;

    try (var body = response.getBody()) {
     bytes = body.readNBytes(maxSizeInBytes + 1);
    }

    if (bytes.length > maxSizeInBytes) {
      log.warn(errorMessage);
      throw new BigSizeContentException(errorMessage);
    }


   return new String(bytes, resolveCharset(response));
  }

  private Charset resolveCharset(ClientHttpResponse response) {
    var contentType = response.getHeaders().getContentType();
    if (contentType != null && contentType.getCharset() != null) {
      return contentType.getCharset();
    }
    return StandardCharsets.UTF_8;
  }
}
