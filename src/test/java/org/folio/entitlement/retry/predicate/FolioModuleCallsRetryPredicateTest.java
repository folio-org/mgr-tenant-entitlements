package org.folio.entitlement.retry.predicate;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class FolioModuleCallsRetryPredicateTest {

  private static final FolioModuleCallsRetryPredicate PREDICATE = new FolioModuleCallsRetryPredicate();

  @ParameterizedTest
  @MethodSource("retryableStatusesProvider")
  void shouldRetry_positive_httpStatusIsClientOrServerError(int status) {
    var error = new IntegrationException("Test", List.of(), status);

    assertThat(PREDICATE.shouldRetry(null, error)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("nonRetryableStatusesProvider")
  void shouldRetry_negative_httpStatusBelow400(int status) {
    var error = new IntegrationException("Test", List.of(), status);

    assertThat(PREDICATE.shouldRetry(null, error)).isFalse();
  }

  @Test
  void shouldRetry_negative_noHttpStatus() {
    var error = new IntegrationException("Test", List.of());

    assertThat(PREDICATE.shouldRetry(null, error)).isFalse();
  }

  @Test
  void shouldRetry_negative_otherExceptionType() {
    assertThat(PREDICATE.shouldRetry(null, new IllegalStateException("Test"))).isFalse();
  }

  private static Stream<Integer> retryableStatusesProvider() {
    return Stream.of(400, 404, 500, 503);
  }

  private static Stream<Integer> nonRetryableStatusesProvider() {
    return Stream.of(200, 301, 399);
  }
}
