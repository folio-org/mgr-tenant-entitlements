package org.folio.entitlement.configuration.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
@EnableCaching
@ConditionalOnProperty("application.keycloak.enabled")
public class CacheConfiguration {

  public static final String ACCESS_TOKEN = "access-token";

  @Bean
  public CacheManager accessTokenCacheManager(Caffeine caffeine) {
    var cacheManager = new CaffeineCacheManager(ACCESS_TOKEN);
    cacheManager.setCaffeine(caffeine);
    return cacheManager;
  }

  @Bean
  public Caffeine<String, AccessTokenResponse> accessTokenCaffeine(AccessTokenExpiry accessTokenExpiry,
                                                                   KeycloakConfigurationProperties keycloakProperties) {
    return Caffeine.newBuilder()
      .initialCapacity(10)
      .expireAfter(accessTokenExpiry)
      .maximumSize(keycloakProperties.getAuthorizationCacheMaxSize())
      .removalListener((k, jwt, cause) -> log.debug("Cached access token removed: key={}, cause={}", k, cause));
  }
}
