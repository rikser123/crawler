package rikser123.crawler.repository.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import rikser123.bundle.repository.entity.OutboxMessage;
import rikser123.crawler.dto.queryResponse.MessageQueryResponseDtoOutbox;

@Entity
@Table(name = "search_response_message")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class SearchResponseOutboxMessage extends OutboxMessage {
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb", name = "dto", nullable = false)
  private MessageQueryResponseDtoOutbox dto;
}
