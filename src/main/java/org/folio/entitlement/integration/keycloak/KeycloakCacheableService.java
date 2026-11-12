package org.folio.entitlement.integration.keycloak;

import static org.folio.entitlement.configuration.cache.CacheConfiguration.ACCESS_TOKEN;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
@ConditionalOnProperty("application.keycloak.enabled")
public class KeycloakCacheableService {

  public static final String ADMIN_TOKEN_KEY = "admin";

  private final Keycloak keycloak;

  /**
   * Obtains the token of the admin client. It does not depend on the calling user, so a single entry is shared by all
   * callers and concurrent misses are collapsed into one request to Keycloak.
   *
   * @param userToken the caller's token, not used to build the admin client token
   * @return admin client access token response
   */
  @Cacheable(cacheNames = ACCESS_TOKEN, key = "'" + ADMIN_TOKEN_KEY + "'", sync = true)
  public AccessTokenResponse getAccessToken(String userToken) {
    return keycloak.tokenManager()
      .grantToken();
  }
}
