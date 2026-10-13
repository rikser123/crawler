package rikser123.crawler.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import rikser123.crawler.repository.entity.CustomPrompt;
import rikser123.crawler.repository.entity.CustomPromptMode;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomPromptRepository extends JpaRepository<CustomPrompt, UUID> {
  List<CustomPrompt> findAllByUserIdAndModeOrderByCreatedCreatedDesc(UUID userId, CustomPromptMode mode);
}
