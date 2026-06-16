package rikser123.crawler.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;


@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT20S")
public class SchedulerConfig {
  @Value("${spring.datasource.hikari.schema}")
  private String schemaDB;

  @Bean
  public LockProvider lockProvider(DataSource dataSource) {
    String tableName = StringUtils.isNotBlank(schemaDB) ? "\"" + schemaDB + "\".shedlock" : "shedlock";
    return new JdbcTemplateLockProvider(dataSource, tableName);
  }
}
