package org.folio.entitlement.configuration;

import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "application.platform-lock")
public class PlatformLockConfigurationProperties {

  /**
   * Value of the {@code Retry-After} header, in seconds, returned with a 409 response when the platform lock is held.
   */
  @Positive
  private int retryAfterSeconds = 5;
}
