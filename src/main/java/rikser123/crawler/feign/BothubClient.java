package rikser123.crawler.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import rikser123.crawler.dto.BothubRequestDto;
import rikser123.crawler.dto.BothubResponseDto;

@FeignClient(
  name = "bothub-client",
  url = "${bothub.url}"
)
public interface BothubClient {
  @PostMapping("/responses")
  BothubResponseDto getResponses(
    @RequestBody BothubRequestDto bothubRequestDto,
    @RequestHeader("Authorization") String authorization
  );
}