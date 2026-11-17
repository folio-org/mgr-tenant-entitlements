package org.folio.entitlement.integration.folio.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.folio.common.configuration.properties.TlsProperties;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
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
  @Valid
  @NotNull
  private TenantJobProperties tenantJob = new TenantJobProperties();

  /**
   * Settings for waiting until a module completes its asynchronous tenant job.
   */
  @Data
  public static class TenantJobProperties {

    /**
     * Maximum time to wait for a tenant job to complete.
     */
    @NotNull
    @DurationMin(millis = 1)
    private Duration timeout = Duration.ofMinutes(30);

    /**
     * How long a single poll may be held by the module while the job is not complete (long poll).
     */
    @NotNull
    @DurationMin(millis = 0)
    private Duration wait = Duration.ofSeconds(20);

    /**
     * Initial pause between polls, used when the module answers immediately.
     */
    @NotNull
    @DurationMin(millis = 0)
    private Duration delay = Duration.ofSeconds(1);

    /**
     * Upper bound of the pause between polls.
     */
    @NotNull
    @DurationMin(millis = 1)
    private Duration maxDelay = Duration.ofSeconds(10);

    /**
     * Factor applied to the pause after every poll.
     */
    @DecimalMin("1.0")
    private double multiplier = 2.0;
  }
}
