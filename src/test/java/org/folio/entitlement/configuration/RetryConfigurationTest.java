package org.folio.entitlement.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.folio.entitlement.configuration.RetryConfigurationProperties.RetryBackoffConfigProps;
import org.folio.entitlement.configuration.RetryConfigurationProperties.RetryConfigProps;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.retry.predicate.FolioModuleCallsRetryPredicate;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class RetryConfigurationTest {

  private static final FolioModuleCallsRetryPredicate PREDICATE = new FolioModuleCallsRetryPredicate();

  @Test
  void retrySpec_positive_attemptsAreMappedToRetries() {
    var config = config(3, 1000, 30000, 5);

    var spec = RetryConfiguration.retrySpec(IntegrationException.class, PREDICATE, config);

    assertThat(spec.maxRetries()).isEqualTo(2);
    assertThat(spec.delay()).isEqualTo(Duration.ofSeconds(1));
    assertThat(spec.maxDelay()).isEqualTo(Duration.ofSeconds(30));
    assertThat(spec.multiplier()).isEqualTo(5.0);
    assertThat(spec.timeout()).isEqualTo(Duration.ZERO);
    assertThat(spec.predicate()).isSameAs(PREDICATE);
    assertThat(spec.includes()).containsExactly(IntegrationException.class);
  }

  @Test
  void retrySpec_positive_singleAttemptMeansNoRetries() {
    var spec = RetryConfiguration.retrySpec(IntegrationException.class, PREDICATE, config(1, 1, 1, 1));

    assertThat(spec.maxRetries()).isZero();
  }

  @Test
  void retrySpec_negative_invalidMultiplierFailsFast() {
    var config = config(3, 1000, 30000, 0);

    assertThatThrownBy(() -> RetryConfiguration.retrySpec(IntegrationException.class, PREDICATE, config))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("multiplier");
  }

  private static RetryConfigProps config(int max, int delay, int maxDelay, int multiplier) {
    var backoff = RetryBackoffConfigProps.builder().delay(delay).maxdelay(maxDelay).multiplier(multiplier).build();
    return RetryConfigProps.builder().max(max).backoff(backoff).build();
  }
}
