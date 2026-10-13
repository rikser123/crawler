package rikser123.crawler.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "custom_prompt")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CustomPrompt {
  @Id
  @Column(name = "id")
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "name", nullable = false, length = 200)
  private String name;

  @Column(name = "text", nullable = false, length = 2000)
  private String text;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "mode", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  private CustomPromptMode mode;

  @Column(name = "created", updatable = false, nullable = false)
  @CreationTimestamp
  private Instant created;

  @Column(name = "updated", insertable = false)
  @UpdateTimestamp
  private Instant updated;
}
