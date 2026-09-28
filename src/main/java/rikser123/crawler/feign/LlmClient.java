package rikser123.crawler.feign;

import feign.Request;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import rikser123.crawler.config.LlmFeignConfig;
import rikser123.crawler.dto.llm.LlmRequestDto;
import rikser123.crawler.dto.llm.LlmResponseDto;

@FeignClient(
  name = "llm-client",
  url = "${llm.url}"
)
public interface LlmClient {
  @PostMapping("/chat/completions")
  LlmResponseDto getResponses(
    @RequestBody LlmRequestDto llmRequestDto,
    @RequestHeader("Authorization") String authorization,
    Request.Options options
  );
}
