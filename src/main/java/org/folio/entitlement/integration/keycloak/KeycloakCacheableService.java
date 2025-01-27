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

  private final Keycloak keycloak;

  @Cacheable(cacheNames = ACCESS_TOKEN, key = "#userToken")
  public AccessTokenResponse getAccessToken(String userToken) {
    return keycloak.tokenManager()
      .grantToken();
  }
}
