package org.folio.entitlement.integration.folio.configuration;

import java.time.Duration;
import lombok.Data;
import org.folio.common.configuration.properties.TlsProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "application.clients.folio")
public class FolioClientConfigurationProperties {

  /**
   * Connect timeout for folio-client.
   */
  private Duration connectTimeout = Duration.ofSeconds(10);

  /**
   * Read timeout for folio-client.
   */
  private Duration readTimeout = Duration.ofSeconds(60);

  /**
   * Max time to wait for an async tenant install job (RMB Tenant API) to complete.
   */
  private Duration tenantApiJobTimeout = Duration.ofMinutes(30);

  /**
   * Delay between tenant job status checks.
   */
  private Duration tenantApiJobPollInterval = Duration.ofSeconds(2);

  /**
   * TLS properties for folio-client.
   */
  private TlsProperties tls;
}
