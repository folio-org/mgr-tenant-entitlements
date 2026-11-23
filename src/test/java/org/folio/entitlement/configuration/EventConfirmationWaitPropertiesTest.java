package org.folio.entitlement.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@UnitTest
class EventConfirmationWaitPropertiesTest {

  private static final Duration SWEEPER_TIMEOUT = Duration.ofMinutes(90);

  @Test
  void defaults_positive() {
    var properties = new EventConfirmationWaitProperties();

    assertThat(properties.isEnabled()).isFalse();
    assertThat(properties.getPollInterval()).isEqualTo(Duration.ofSeconds(2));
    assertThat(properties.getMaxWait()).isEqualTo(Duration.ofMinutes(5));
    assertThatCode(() -> properties.validate(SWEEPER_TIMEOUT)).doesNotThrowAnyException();
  }

  @Test
  void validate_positive_enabledWithDefaults() {
    var properties = new EventConfirmationWaitProperties();
    properties.setEnabled(true);

    assertThatCode(() -> properties.validate(SWEEPER_TIMEOUT)).doesNotThrowAnyException();
    assertThatCode(properties::validateOnStartup).doesNotThrowAnyException();
  }

  @Test
  void validate_positive_disabledIgnoresInvalidSettings() {
    var properties = new EventConfirmationWaitProperties();
    properties.setPollInterval(Duration.ZERO);
    properties.setMaxWait(SWEEPER_TIMEOUT);
    properties.setAsyncConfirmationTimeout(Duration.ofMinutes(3));

    assertThatCode(() -> properties.validate(SWEEPER_TIMEOUT)).doesNotThrowAnyException();
    assertThatCode(properties::validateOnStartup).doesNotThrowAnyException();
  }

  @Test
  void validate_negative_maxWaitNotBelowSweeperTimeout() {
    var properties = new EventConfirmationWaitProperties();
    properties.setEnabled(true);
    properties.setMaxWait(SWEEPER_TIMEOUT);

    assertThatThrownBy(() -> properties.validate(SWEEPER_TIMEOUT))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("must be less than application.async-confirmation.timeout");
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1})
  void validate_negative_nonPositivePollInterval(long seconds) {
    var properties = new EventConfirmationWaitProperties();
    properties.setEnabled(true);
    properties.setPollInterval(Duration.ofSeconds(seconds));

    assertThatThrownBy(() -> properties.validate(SWEEPER_TIMEOUT))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("poll-interval");
  }

  @ParameterizedTest
  @ValueSource(longs = {0, -1})
  void validate_negative_nonPositiveMaxWait(long seconds) {
    var properties = new EventConfirmationWaitProperties();
    properties.setEnabled(true);
    properties.setMaxWait(Duration.ofSeconds(seconds));

    assertThatThrownBy(() -> properties.validate(SWEEPER_TIMEOUT))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("max-wait");
  }

  @Test
  void validateOnStartup_negative_usesConfiguredSweeperTimeout() {
    var properties = new EventConfirmationWaitProperties();
    properties.setEnabled(true);
    properties.setAsyncConfirmationTimeout(Duration.ofMinutes(3));

    assertThatThrownBy(properties::validateOnStartup)
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("must be less than application.async-confirmation.timeout");
  }
}
