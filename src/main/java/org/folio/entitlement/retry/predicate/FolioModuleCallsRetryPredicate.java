package org.folio.entitlement.retry.predicate;

import java.lang.reflect.Method;
import org.folio.entitlement.integration.IntegrationException;
import org.springframework.resilience.retry.MethodRetryPredicate;

/**
 * Decides whether a failed FOLIO module call is retried: only {@link IntegrationException}s caused by an HTTP error
 * response (status 400 and above) are retried.
 */
public class FolioModuleCallsRetryPredicate implements MethodRetryPredicate {

  /**
   * Checks that the failure is an {@link IntegrationException} with a cause HTTP status of 400 or above.
   *
   * @param method invoked method
   * @param throwable failure thrown by the method
   * @return {@code true} if the call should be retried, {@code false} otherwise
   */
  @Override
  public boolean shouldRetry(Method method, Throwable throwable) {
    return throwable instanceof IntegrationException exception
      && exception.getCauseHttpStatus() != null
      && exception.getCauseHttpStatus() >= 400;
  }
}
