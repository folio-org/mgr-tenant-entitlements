package org.folio.entitlement.integration.folio;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConditionalOnProperty(name = "application.okapi.enabled", havingValue = "false")
@ConfigurationProperties(prefix = "application.clients.folio")
public class FolioClientConfiguration {

  /**
   * Connect timeout for folio-client.
   */
  private Duration connectTimeout = Duration.ofSeconds(10);

  /**
   * Read timeout for folio-client.
   */
  private Duration readTimeout = Duration.ofSeconds(60);
}
