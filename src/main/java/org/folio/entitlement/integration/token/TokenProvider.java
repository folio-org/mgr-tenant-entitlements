package org.folio.entitlement.integration.token;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.keycloak.KeycloakCacheableService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Provides access tokens for internal operations between mgr-* services.
 * Uses KeycloakCacheableService to obtain and cache tokens, ensuring fresh tokens
 * are available even during long-running operations.
 */
@Log4j2
@Component
@RequiredArgsConstructor
public class TokenProvider {

  private KeycloakCacheableService keycloakCacheableService;

  @Value("${application.keycloak.enabled}")
  private boolean keycloakEnabled;

  @Autowired(required = false)
  public void setKeycloakCacheableService(KeycloakCacheableService keycloakCacheableService) {
    this.keycloakCacheableService = keycloakCacheableService;
  }

  /**
   * Returns a fresh access token, either from the Keycloak cache or the provided user token.
   * When Keycloak is enabled, this method obtains a token using client credentials flow
   * via KeycloakCacheableService, which manages token caching and expiry.
   * When Keycloak is disabled, the provided user token is returned as-is.
   *
   * @param userToken the user-provided token to use as cache key or fallback
   * @return a valid access token
   */
  public String getToken(String userToken) {
    if (keycloakEnabled && keycloakCacheableService != null) {
      log.debug("Obtaining access token from Keycloak");
      return keycloakCacheableService.getAccessToken(userToken).getToken();
    }
    log.debug("Keycloak disabled, using provided token");
    return userToken;
  }
}
