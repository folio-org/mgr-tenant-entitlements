package org.folio.entitlement.integration.keycloak;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.token.AdminTokenProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Provides the Keycloak system token for internal operations between mgr-* services.
 * Uses KeycloakCacheableService to obtain and cache the token, ensuring a fresh token
 * is available even during long-running operations. The token is the same for all callers.
 */
@Log4j2
@Component
@ConditionalOnProperty(value = "application.keycloak.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KeycloakAdminTokenProvider implements AdminTokenProvider {

  private final KeycloakCacheableService keycloakCacheableService;

  /**
   * Retrieves the Keycloak system token, from the cache when a valid one is available.
   *
   * @param userToken the user token of the caller; it does not affect the returned token
   * @return the Keycloak system token
   */
  public String getToken(String userToken) {
    log.debug("Obtaining access token from Keycloak");
    return keycloakCacheableService.getAccessToken(userToken).getToken();
  }

  /**
   * Tells that the system token is the same for all callers, so downstream responses can be shared between them.
   *
   * @return always {@code true}
   */
  @Override
  public boolean isSharedIdentity() {
    return true;
  }
}
