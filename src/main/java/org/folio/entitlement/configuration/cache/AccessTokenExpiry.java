package org.folio.entitlement.configuration.cache;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@ConditionalOnProperty("application.keycloak.enabled")
public class AccessTokenExpiry implements Expiry<String, AccessTokenResponse> {

  private final long expireOffset;

  public AccessTokenExpiry(@Autowired KeycloakConfigurationProperties keycloakProperties) {
    this.expireOffset = MILLISECONDS.toNanos(keycloakProperties.getAuthorizationCacheTtlOffset());
  }

  @Override
  public long expireAfterCreate(String key, AccessTokenResponse value, long currentTime) {
    return SECONDS.toNanos(value.getExpiresIn()) - expireOffset;
  }

  @Override
  public long expireAfterUpdate(String key, AccessTokenResponse value, long currentTime, long currentDuration) {
    return currentDuration;
  }

  @Override
  public long expireAfterRead(String key, AccessTokenResponse value, long currentTime, long currentDuration) {
    return currentDuration;
  }
}
