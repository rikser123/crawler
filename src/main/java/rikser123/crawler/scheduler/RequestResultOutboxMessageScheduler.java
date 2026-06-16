package rikser123.crawler.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import rikser123.bundle.repository.entity.OutboxMessageStatus;
import rikser123.crawler.producer.RequestResultStatusProducer;
import rikser123.crawler.service.RequestResultOutboxMessageService;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class RequestResultOutboxMessageScheduler {
  private final RequestResultStatusProducer requestResultStatusProducer;
  private final RequestResultOutboxMessageService requestResultOutboxMessageService;

  @Scheduled(fixedDelayString = "${kafka.scheduler-delay}")
  @SchedulerLock(
    name = "RequestResultOutboxMessageScheduler",
    lockAtLeastFor = "3s",
    lockAtMostFor = "10s"
  )
  public void schedule() {
    log.info("RequestResultOutboxMessageScheduler started");

    var createdMessages = requestResultOutboxMessageService.findAllByStatus(OutboxMessageStatus.CREATED);

    if (createdMessages.isEmpty()) {
      log.info("RequestResultOutboxMessageScheduler finished, no messages");
      return;
    }

    var futures = createdMessages.stream().map(requestResultStatusProducer::send).toList();
    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    log.info("RequestResultOutboxMessageScheduler finished");
  }
}

