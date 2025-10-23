package org.folio.entitlement.integration.keycloak;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.token.AdminTokenProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Provides access tokens for internal operations between mgr-* services.
 * Uses KeycloakCacheableService to obtain and cache tokens, ensuring fresh tokens
 * are available even during long-running operations.
 */
@Log4j2
@Component
@ConditionalOnProperty(value = "application.keycloak.enabled", havingValue = "true")
@RequiredArgsConstructor
public class KeycloakAdminTokenProvider implements AdminTokenProvider {

  private final KeycloakCacheableService keycloakCacheableService;

  /**
   * Retrieves a fresh access token from Keycloak using the provided user token.
   *
   * @param userToken the user token to authenticate the request
   * @return a fresh access token from Keycloak
   */
  public String getToken(String userToken) {
    log.debug("Obtaining access token from Keycloak");
    return keycloakCacheableService.getAccessToken(userToken).getToken();
  }
}
