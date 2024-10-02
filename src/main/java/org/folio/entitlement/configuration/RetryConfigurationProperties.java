package org.folio.entitlement.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "retries")
public class RetryConfigurationProperties {

  private RetryConfigProps module;
  private RetryConfigProps keycloak;
  private RetryConfigProps kong;

  @Data
  public static class RetryConfigProps {
    private int max = 3;
    private RetryBackoffConfigProps backoff;
  }

  @Data
  public static class RetryBackoffConfigProps {
    private int delay = 1000;
    private int maxdelay = 30000;
    private int multiplier = 5;
  }
}
