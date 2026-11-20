package org.folio.entitlement.configuration;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
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

  /**
   * Async confirmation sweeper timeout, used only to verify that {@link #maxWait} is shorter.
   */
  @Value("${application.async-confirmation.timeout:90m}")
  private Duration asyncConfirmationTimeout = Duration.ofMinutes(90);

  @PostConstruct
  void validateOnStartup() {
    validate(asyncConfirmationTimeout);
  }

  /**
   * Verifies that durations are positive and the max wait is shorter than the async confirmation sweeper timeout.
   * Does nothing when the wait is disabled, so unused settings never block startup.
   *
   * @param sweeperTimeout - async confirmation timeout
   */
  public void validate(Duration sweeperTimeout) {
    if (!enabled) {
      return;
    }
    if (pollInterval.isZero() || pollInterval.isNegative()) {
      throw new IllegalStateException("application.event-confirmation-wait.poll-interval (" + pollInterval
        + ") must be positive");
    }
    if (maxWait.isZero() || maxWait.isNegative()) {
      throw new IllegalStateException("application.event-confirmation-wait.max-wait (" + maxWait
        + ") must be positive");
    }
    if (maxWait.compareTo(sweeperTimeout) >= 0) {
      throw new IllegalStateException("application.event-confirmation-wait.max-wait (" + maxWait
        + ") must be less than application.async-confirmation.timeout (" + sweeperTimeout + ")");
    }
  }
}
