package rikser123.crawler.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rikser123.bundle.repository.entity.OutboxMessageStatus;
import rikser123.crawler.producer.QueryAnalysisProducer;
import rikser123.crawler.service.SearchQueryMessageService;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class UserQueryMessageScheduler {
  private final QueryAnalysisProducer queryAnalysisProducer;
  private final SearchQueryMessageService searchQueryMessageService;

  @Scheduled(fixedDelayString = "${kafka.scheduler-delay}")
  @SchedulerLock(
    name = "UserQueryMessageScheduler",
    lockAtLeastFor = "3s",
    lockAtMostFor = "10s"
  )
  public void schedule() {
    log.info("UserQueryMessageScheduler started");

    var createdMessages = searchQueryMessageService.findAllByStatus(OutboxMessageStatus.CREATED);

    if (createdMessages.isEmpty()) {
      log.info("UserQueryMessageScheduler finished, no messages");
      return;
    }

    var futures = createdMessages.stream().map(queryAnalysisProducer::send).toList();
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    log.info("UserQueryMessageScheduler finished");
  }
}
