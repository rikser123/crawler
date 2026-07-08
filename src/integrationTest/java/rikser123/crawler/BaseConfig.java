package rikser123.crawler;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.mockserver.integration.ClientAndServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

@ActiveProfiles("integration-test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(addFilters = false)
@WithMockUser(authorities = {"CHECK_SPELLS", "CREATE_REQUEST", "VIEW_REQUEST"})
@EmbeddedKafka(
  topics = {"REQUEST", "QUERY_RESULT", "QUERY_ANALYSIS_TOPIC"}
)
@Testcontainers
public abstract class BaseConfig {
  protected static ClientAndServer mockServer;

  @Container
  @ServiceConnection
  private static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

  @Autowired
  protected MockMvc client;

  @BeforeAll
  static void initMock() {
    mockServer = startClientAndServer(8081);
  }

  @AfterAll
  static void stopMock() {
    mockServer.stop();
  }
}
