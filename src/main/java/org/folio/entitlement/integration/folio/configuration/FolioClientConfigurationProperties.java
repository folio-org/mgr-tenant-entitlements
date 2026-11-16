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
   * TLS properties for folio-client.
   */
  private TlsProperties tls;

  /**
   * Polling of asynchronous tenant jobs.
   */
  private TenantJobProperties tenantJob = new TenantJobProperties();

  @Data
  public static class TenantJobProperties {

    /**
     * Maximum time to wait for a tenant job to complete.
     */
    private Duration timeout = Duration.ofMinutes(30);

    /**
     * How long a single poll may be held by the module while the job is not complete (long poll).
     */
    private Duration wait = Duration.ofSeconds(20);

    /**
     * Initial pause between polls, used when the module answers immediately.
     */
    private Duration delay = Duration.ofSeconds(1);

    /**
     * Upper bound of the pause between polls.
     */
    private Duration maxDelay = Duration.ofSeconds(10);

    /**
     * Factor applied to the pause after every poll.
     */
    private double multiplier = 2.0;
  }
}
