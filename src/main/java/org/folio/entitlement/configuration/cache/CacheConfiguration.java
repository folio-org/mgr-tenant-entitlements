package org.folio.entitlement.configuration.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.configuration.cache.EntitlementCacheProperties.CacheSettings;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
@EnableCaching
public class CacheConfiguration {

  public static final String ACCESS_TOKEN = "access-token";
  public static final String TENANT_ID = "tenant-id";
  public static final String APPLICATION_DESCRIPTOR = "application-descriptor";

  @Bean
  public CacheManager cacheManager(List<Cache> caches) {
    var cacheManager = new SimpleCacheManager();
    cacheManager.setCaches(caches);
    return cacheManager;
  }

  @Bean
  public Cache tenantIdCache(EntitlementCacheProperties properties) {
    return buildCache(TENANT_ID, properties.getTenantId());
  }

  @Bean
  public Cache applicationDescriptorCache(EntitlementCacheProperties properties) {
    return buildCache(APPLICATION_DESCRIPTOR, properties.getApplicationDescriptor());
  }

  @Bean
  @ConditionalOnProperty("application.keycloak.enabled")
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Cache accessTokenCache(AccessTokenExpiry accessTokenExpiry,
    KeycloakConfigurationProperties keycloakProperties) {
    Caffeine<String, AccessTokenResponse> caffeine = Caffeine.newBuilder()
      .initialCapacity(10)
      .expireAfter(accessTokenExpiry)
      .maximumSize(keycloakProperties.getAuthorizationCacheMaxSize())
      .removalListener((k, jwt, cause) -> log.debug("Cached access token removed: key={}, cause={}", k, cause));
    return new CaffeineCache(ACCESS_TOKEN, (com.github.benmanes.caffeine.cache.Cache) caffeine.build());
  }

  private static Cache buildCache(String name, CacheSettings settings) {
    return new CaffeineCache(name, Caffeine.newBuilder()
      .maximumSize(settings.getMaxSize())
      .expireAfterWrite(settings.getTtl())
      .recordStats()
      .build());
  }
}
