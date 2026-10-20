package rikser123.crawler.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import rikser123.crawler.repository.entity.CustomPrompt;
import rikser123.crawler.repository.entity.CustomPromptMode;

import java.util.List;
import java.util.UUID;

@Repository
public interface CustomPromptRepository extends JpaRepository<CustomPrompt, UUID> {
  @Query("""
      SELECT cp FROM CustomPrompt cp
      WHERE cp.userId = :userId
        AND (cast(:mode as string) IS NULL OR cp.mode = :mode)
      ORDER BY cp.created DESC
      """)
  List<CustomPrompt> findAllByUserIdAndModeOrderByCreatedDesc(UUID userId, CustomPromptMode mode);
}
