package org.folio.entitlement.configuration.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.configuration.cache.EntitlementCacheProperties.CacheSettings;
import org.keycloak.representations.AccessTokenResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * In-memory caches of the module. Every cache is defined as a separate {@link Cache} bean with its own expiry rules,
 * and all of them are registered in a single {@link CacheManager}.
 */
@Log4j2
@Configuration
@EnableCaching
public class CacheConfiguration {

  /**
   * Name of the cache with the Keycloak system token, available only when Keycloak integration is enabled.
   */
  public static final String ACCESS_TOKEN = "access-token";

  /**
   * Name of the cache with tenants resolved by tenant name.
   */
  public static final String TENANT_ID = "tenant-id";

  /**
   * Name of the cache with application descriptors by application id.
   */
  public static final String APPLICATION_DESCRIPTOR = "application-descriptor";

  /**
   * Registers all {@link Cache} beans in a single cache manager.
   *
   * @param caches - all cache beans available in the application context
   * @return {@link CacheManager} that holds the given caches
   */
  @Bean
  public CacheManager cacheManager(List<Cache> caches) {
    var cacheManager = new SimpleCacheManager();
    cacheManager.setCaches(caches);
    return cacheManager;
  }

  /**
   * Creates the tenant lookup cache.
   *
   * @param properties - cache settings
   * @return {@link Cache} with entries that expire after the configured time to live
   */
  @Bean
  public Cache tenantIdCache(EntitlementCacheProperties properties) {
    return buildCache(TENANT_ID, properties.getTenantId());
  }

  /**
   * Creates the application descriptor cache.
   *
   * @param properties - cache settings
   * @return {@link Cache} with entries that expire after the configured time to live
   */
  @Bean
  public Cache applicationDescriptorCache(EntitlementCacheProperties properties) {
    return buildCache(APPLICATION_DESCRIPTOR, properties.getApplicationDescriptor());
  }

  /**
   * Creates the cache of the Keycloak system token.
   *
   * <p>The token does not depend on the calling user, so the cache holds a single entry that expires shortly before
   * the token itself.</p>
   *
   * @param accessTokenExpiry - expiry rule based on the token lifetime
   * @return {@link Cache} for the Keycloak system token
   */
  @Bean
  @ConditionalOnProperty("application.keycloak.enabled")
  @SuppressWarnings({"unchecked", "rawtypes"})
  public Cache accessTokenCache(AccessTokenExpiry accessTokenExpiry) {
    Caffeine<String, AccessTokenResponse> caffeine = Caffeine.newBuilder()
      .expireAfter(accessTokenExpiry)
      .removalListener((k, jwt, cause) -> log.debug("Cached access token removed: key={}, cause={}", k, cause));
    return new CaffeineCache(ACCESS_TOKEN, (com.github.benmanes.caffeine.cache.Cache) caffeine.build());
  }

  private static Cache buildCache(String name, CacheSettings settings) {
    var cache = new CaffeineCache(name, Caffeine.newBuilder()
      .maximumSize(settings.getMaxSize())
      .expireAfterWrite(settings.getTtl())
      .recordStats()
      .build());
    
    log.debug("Cache created: name = {}, maxSize = {}, ttl = {}", name, settings.getMaxSize(), settings.getTtl());

    return cache;
  }
}
