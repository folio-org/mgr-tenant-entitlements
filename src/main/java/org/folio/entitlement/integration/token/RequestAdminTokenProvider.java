package org.folio.entitlement.integration.token;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Pass-through token provider used when Keycloak integration is disabled: calls to other mgr-* services are made
 * with the caller's own token, so every downstream request is authorized for that caller.
 */
@Log4j2
@Component
@ConditionalOnProperty(value = "application.keycloak.enabled", havingValue = "false")
public class RequestAdminTokenProvider implements AdminTokenProvider {

  /**
   * Retrieves a fresh access token from the request context using the provided user token.
   *
   * @param userToken the user token to authenticate the request
   * @return a fresh access token from the request context
   */
  public String getToken(String userToken) {
    log.debug("Obtaining access token from request context");
    return userToken;
  }

  /**
   * Tells that the caller's token is forwarded, so downstream responses must not be shared between callers.
   *
   * @return always {@code false}
   */
  @Override
  public boolean isSharedIdentity() {
    return false;
  }
}
