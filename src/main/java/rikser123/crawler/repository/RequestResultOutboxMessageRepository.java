package rikser123.crawler.repository;

import org.springframework.stereotype.Repository;
import rikser123.bundle.repository.OutboxMessageRepository;
import rikser123.crawler.repository.entity.RequestResultOutboxMessage;

@Repository
public interface RequestResultOutboxMessageRepository extends OutboxMessageRepository<RequestResultOutboxMessage> {
}
