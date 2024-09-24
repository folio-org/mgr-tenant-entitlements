package org.folio.entitlement.retry.keycloak;

import org.folio.entitlement.retry.annotations.KeycloakCallsRetryable;
import org.folio.entitlement.utils.SafeCallable;
import org.springframework.stereotype.Service;

@KeycloakCallsRetryable
@Service
public class KeycloakRetrySupportService {

  public <T> T callWithRetry(SafeCallable<T> keycloakCall) {
    return keycloakCall.call();
  }

  public void runWithRetry(Runnable keycloakCall) {
    keycloakCall.run();
  }
}
