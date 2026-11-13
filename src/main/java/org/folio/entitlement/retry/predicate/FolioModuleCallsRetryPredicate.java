package org.folio.entitlement.retry.predicate;

import java.lang.reflect.Method;
import org.folio.entitlement.integration.IntegrationException;
import org.springframework.resilience.retry.MethodRetryPredicate;

public class FolioModuleCallsRetryPredicate implements MethodRetryPredicate {

  @Override
  public boolean shouldRetry(Method method, Throwable throwable) {
    return throwable instanceof IntegrationException exception
      && exception.getCauseHttpStatus() != null
      && exception.getCauseHttpStatus() >= 400;
  }
}
