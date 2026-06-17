package rikser123.crawler.repository;

import org.springframework.stereotype.Repository;
import rikser123.bundle.repository.OutboxMessageRepository;
import rikser123.crawler.repository.entity.SearchResponseOutboxMessage;

@Repository
public interface SearchResponseOutboxMessageRepository extends OutboxMessageRepository<SearchResponseOutboxMessage> {
}
