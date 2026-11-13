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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Validated
@Component
@ConfigurationProperties(prefix = "retries")
public class RetryConfigurationProperties {

  @Valid
  @Builder.Default
  private RetryConfigProps module = new RetryConfigProps();
  @Valid
  @Builder.Default
  private RetryConfigProps keycloak = new RetryConfigProps();

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
    @Valid
    @Builder.Default
    private RetryBackoffConfigProps backoff = new RetryBackoffConfigProps();
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class RetryBackoffConfigProps {

    @Min(0)
    @Builder.Default
    private int delay = 1000;
    @Min(1)
    @Builder.Default
    private int maxdelay = 30000;
    @Min(1)
    @Builder.Default
    private int multiplier = 5;
  }
}
