package org.folio.entitlement.retry.predicate;

import jakarta.ws.rs.WebApplicationException;
import java.lang.reflect.Method;
import org.springframework.resilience.retry.MethodRetryPredicate;

public class KeycloakCallsRetryPredicate implements MethodRetryPredicate {

  @Override
  public boolean shouldRetry(Method method, Throwable throwable) {
    return throwable instanceof WebApplicationException exception
      && exception.getResponse() != null
      && exception.getResponse().getStatus() >= 500;
  }
}
