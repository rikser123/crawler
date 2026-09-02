package rikser123.crawler.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import rikser123.crawler.config.DeepSeekFeignConfig;
import rikser123.crawler.dto.deepseek.DeepSeekRequestDto;
import rikser123.crawler.dto.deepseek.DeepSeekResponseDto;

@FeignClient(
  name = "deepseek-client",
  url = "${deepseek.url}",
  configuration = DeepSeekFeignConfig.class
)
public interface DeepSeekClient {
  @PostMapping("/v1/chat/completions")
  DeepSeekResponseDto getResponses(
    @RequestBody DeepSeekRequestDto deepSeekRequestDto,
    @RequestHeader("Authorization") String authorization
  );
}
