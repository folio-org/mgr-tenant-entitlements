package org.folio.entitlement.retry.predicate;

import jakarta.ws.rs.WebApplicationException;
import java.lang.reflect.Method;
import org.springframework.resilience.retry.MethodRetryPredicate;

/**
 * Decides whether a failed Keycloak call is retried: only {@link WebApplicationException}s with a server error
 * response (status 500 and above) are retried.
 */
public class KeycloakCallsRetryPredicate implements MethodRetryPredicate {

  /**
   * Checks that the failure is a {@link WebApplicationException} with a response status of 500 or above.
   *
   * @param method invoked method
   * @param throwable failure thrown by the method
   * @return {@code true} if the call should be retried, {@code false} otherwise
   */
  @Override
  public boolean shouldRetry(Method method, Throwable throwable) {
    return throwable instanceof WebApplicationException exception
      && exception.getResponse() != null
      && exception.getResponse().getStatus() >= 500;
  }
}
