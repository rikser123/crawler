package rikser123.crawler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;


@SpringBootApplication
@EnableFeignClients(basePackages = "rikser123.crawler.feign")
@Slf4j
public class RikserCrawlerApplication {
  public static void main(String[] args) {
    SpringApplication.run(RikserCrawlerApplication.class, args);
  }
}
