package org.folio.entitlement.configuration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Component
@ConfigurationProperties(prefix = "application.flow-engine")
public class FlowEngineConfigurationProperties {

  /**
   * Defines if flow engine must print results after each execution.
   */
  private Boolean printFlowResult = false;

  /**
   * Number of fork-join pool thread.
   */
  @Min(1)
  @Max(12)
  private int poolThreads = 4;

  /**
   * Defines maximum execution timeout, after flow engine will stop flow execution, not applied to executeAsync().
   */
  @NotNull
  private Duration executionTimeout = Duration.ofMinutes(30);

  /**
   * Provides a maximum amount of latest flows execution statuses to be cached by flow engine.
   */
  @Positive
  private int lastExecutionsStatusCacheSize = 25;

  /**
   * Number of threads for parallel module installation.
   */
  @Min(1)
  @Max(12)
  private int moduleInstallerThreads = 4;
}
