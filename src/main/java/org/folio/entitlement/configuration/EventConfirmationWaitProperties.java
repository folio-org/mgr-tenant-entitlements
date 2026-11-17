package org.folio.entitlement.configuration;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/**
 * Settings of the stage that waits for downstream confirmations of published events.
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "application.event-confirmation-wait")
public class EventConfirmationWaitProperties {

  /**
   * Defines if the wait stage waits for confirmations. When {@code false} the stage is a no-op.
   */
  private boolean enabled = false;

  /**
   * Interval between two checks of the confirmation state.
   */
  @NotNull
  private Duration pollInterval = Duration.ofSeconds(2);

  /**
   * Maximum time to wait for all confirmations before the stage and the flow fail. Must be shorter than
   * {@code application.async-confirmation.timeout}, which acts as a backstop only.
   */
  @NotNull
  private Duration maxWait = Duration.ofMinutes(5);
}
