package org.folio.entitlement.configuration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Retry settings bound from the {@code retries.*} properties, validated on startup.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Validated
@Component
@ConfigurationProperties(prefix = "retries")
public class RetryConfigurationProperties {

  /**
   * Retry settings for calls to FOLIO modules.
   */
  @Valid
  @Builder.Default
  private RetryConfigProps module = new RetryConfigProps();
  /**
   * Retry settings for calls to Keycloak.
   */
  @Valid
  @Builder.Default
  private RetryConfigProps keycloak = new RetryConfigProps();

  /**
   * Retry settings of a single integration: number of attempts and the back-off between them.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class RetryConfigProps {

    /**
     * Maximum number of attempts, the initial call included.
     */
    @Min(1)
    @Builder.Default
    private int max = 3;
    /**
     * Back-off between attempts.
     */
    @Valid
    @Builder.Default
    private RetryBackoffConfigProps backoff = new RetryBackoffConfigProps();
  }

  /**
   * Exponential back-off settings: the pause before retry {@code n} is {@code delay * multiplier^(n-1)}, capped by
   * {@code maxdelay}.
   */
  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class RetryBackoffConfigProps {

    /**
     * Pause before the first retry, in milliseconds.
     */
    @Min(0)
    @Builder.Default
    private int delay = 1000;
    /**
     * Upper bound for the pause between attempts, in milliseconds.
     */
    @Min(1)
    @Builder.Default
    private int maxdelay = 30000;
    /**
     * Factor by which the pause grows after each retry.
     */
    @Min(1)
    @Builder.Default
    private int multiplier = 5;
  }
}
