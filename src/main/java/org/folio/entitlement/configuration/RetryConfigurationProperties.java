package org.folio.entitlement.configuration;

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

  @Builder.Default
  private RetryConfigProps module = new RetryConfigProps();
  @Builder.Default
  private RetryConfigProps keycloak = new RetryConfigProps();

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class RetryConfigProps {

    @Builder.Default
    private int max = 3;
    @Builder.Default
    private RetryBackoffConfigProps backoff = new RetryBackoffConfigProps();
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  @Builder
  public static class RetryBackoffConfigProps {

    @Builder.Default
    private int delay = 1000;
    @Builder.Default
    private int maxdelay = 30000;
    @Builder.Default
    private int multiplier = 5;
  }
}
