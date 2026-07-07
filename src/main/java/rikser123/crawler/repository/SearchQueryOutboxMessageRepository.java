package rikser123.crawler.repository;

import org.springframework.stereotype.Repository;
import rikser123.bundle.repository.OutboxMessageRepository;
import rikser123.crawler.repository.entity.SearchQueryOutboxMessage;

@Repository
public interface SearchQueryOutboxMessageRepository extends OutboxMessageRepository<SearchQueryOutboxMessage> {
}
