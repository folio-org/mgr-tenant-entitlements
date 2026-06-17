package org.folio.entitlement.configuration.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Always-on cache configuration. Carries {@link EnableCaching} so caching works regardless of
 * {@code application.keycloak.enabled} (the keycloak-gated {@link CacheConfiguration} no longer
 * enables caching), and {@link EnableScheduling} for the cache warmer's periodic refresh. Provides a
 * {@code moduleEntitlementsCacheManager} bean that is a Caffeine manager when enabled and a
 * {@link NoOpCacheManager} when disabled — the bean name always resolves so
 * {@code @Cacheable/@CacheEvict(cacheManager = "moduleEntitlementsCacheManager")} is valid in every
 * profile.
 */
@Log4j2
@Configuration
@EnableCaching
@EnableScheduling
@EnableConfigurationProperties(ModuleEntitlementsCacheProperties.class)
public class ModuleEntitlementsCacheConfiguration {

  public static final String MODULE_ENTITLEMENTS_CACHE = "module-entitlements";

  @Bean(name = "moduleEntitlementsCacheManager")
  @ConditionalOnProperty(name = "application.module-entitlements-cache.enabled", havingValue = "true",
    matchIfMissing = true)
  public CacheManager caffeineModuleEntitlementsCacheManager(ModuleEntitlementsCacheProperties properties) {
    var cacheManager = new CaffeineCacheManager(MODULE_ENTITLEMENTS_CACHE);
    cacheManager.setCaffeine(Caffeine.newBuilder()
      .maximumSize(properties.getMaxSize())
      .expireAfterWrite(properties.getTtl())
      .removalListener((k, v, cause) ->
        log.debug("Cached module entitlements removed: key={}, cause={}", k, cause)));
    return cacheManager;
  }

  @Bean(name = "moduleEntitlementsCacheManager")
  @ConditionalOnProperty(name = "application.module-entitlements-cache.enabled", havingValue = "false")
  public CacheManager noOpModuleEntitlementsCacheManager() {
    return new NoOpCacheManager();
  }
}
