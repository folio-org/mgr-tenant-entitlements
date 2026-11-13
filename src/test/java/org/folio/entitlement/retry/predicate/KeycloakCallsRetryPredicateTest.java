package org.folio.entitlement.retry.predicate;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import java.util.stream.Stream;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class KeycloakCallsRetryPredicateTest {

  private static final KeycloakCallsRetryPredicate PREDICATE = new KeycloakCallsRetryPredicate();

  @ParameterizedTest
  @MethodSource("retryableStatusesProvider")
  void shouldRetry_positive_httpStatusIsServerError(int status) {
    var error = new WebApplicationException("test", status);

    assertThat(PREDICATE.shouldRetry(null, error)).isTrue();
  }

  @ParameterizedTest
  @MethodSource("nonRetryableStatusesProvider")
  void shouldRetry_negative_httpStatusBelow500(int status) {
    var error = new WebApplicationException("test", status);

    assertThat(PREDICATE.shouldRetry(null, error)).isFalse();
  }

  @Test
  void shouldRetry_negative_otherExceptionType() {
    assertThat(PREDICATE.shouldRetry(null, new IllegalStateException("Test"))).isFalse();
  }

  @Test
  void shouldRetry_negative_folioModuleError() {
    var error = new IntegrationException("Test", List.of(), 500);

    assertThat(PREDICATE.shouldRetry(null, error)).isFalse();
  }

  private static Stream<Integer> retryableStatusesProvider() {
    return Stream.of(500, 503, 599);
  }

  private static Stream<Integer> nonRetryableStatusesProvider() {
    return Stream.of(400, 404, 499);
  }
}
