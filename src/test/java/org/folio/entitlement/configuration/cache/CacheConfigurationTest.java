package org.folio.entitlement.configuration.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.entitlement.configuration.cache.CacheConfiguration.ACCESS_TOKEN;
import static org.folio.entitlement.configuration.cache.CacheConfiguration.APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.configuration.cache.CacheConfiguration.TENANT_ID;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.folio.entitlement.configuration.cache.EntitlementCacheProperties.CacheSettings;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.representations.AccessTokenResponse;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CacheConfigurationTest {

  private static final String KEY = "key";
  private static final String VALUE = "value";
  private static final long TTL_OFFSET_MILLIS = 5000;

  @Mock private KeycloakConfigurationProperties keycloakProperties;

  private final CacheConfiguration configuration = new CacheConfiguration();

  @Test
  void cacheManager_positive_registersAllGivenCaches() {
    var properties = properties(Duration.ofMinutes(1), 10, Duration.ofMinutes(1), 10);
    var tenantIdCache = configuration.tenantIdCache(properties);
    var descriptorCache = configuration.applicationDescriptorCache(properties);

    var cacheManager = (SimpleCacheManager) configuration.cacheManager(List.of(tenantIdCache, descriptorCache));
    cacheManager.afterPropertiesSet();

    assertThat(cacheManager.getCacheNames()).containsExactlyInAnyOrder(TENANT_ID, APPLICATION_DESCRIPTOR);
    assertThat(cacheManager.getCache(TENANT_ID)).isSameAs(tenantIdCache);
    assertThat(cacheManager.getCache(APPLICATION_DESCRIPTOR)).isSameAs(descriptorCache);
  }

  @Test
  void tenantIdCache_positive_retainsEntryWithinTtl() {
    var cache = configuration.tenantIdCache(properties(Duration.ofMinutes(1), 10, Duration.ofMinutes(1), 10));

    cache.put(KEY, VALUE);

    assertThat(cache.getName()).isEqualTo(TENANT_ID);
    assertThat(cache.get(KEY, String.class)).isEqualTo(VALUE);
  }

  @Test
  void tenantIdCache_positive_entryExpiresAfterTtl() {
    var cache = configuration.tenantIdCache(properties(Duration.ofMillis(50), 10, Duration.ofMinutes(1), 10));

    cache.put(KEY, VALUE);

    await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertThat(cache.get(KEY)).isNull());
  }

  @Test
  void tenantIdCache_positive_zeroTtlDisablesCache() {
    var cache = configuration.tenantIdCache(properties(Duration.ZERO, 10, Duration.ofMinutes(1), 10));

    cache.put(KEY, VALUE);

    assertThat(cache.get(KEY)).isNull();
  }

  @Test
  void tenantIdCache_positive_zeroMaxSizeDisablesCache() {
    var cache = configuration.tenantIdCache(properties(Duration.ofMinutes(1), 0, Duration.ofMinutes(1), 10));

    cache.put(KEY, VALUE);
    ((CaffeineCache) cache).getNativeCache().cleanUp();

    assertThat(cache.get(KEY)).isNull();
  }

  @Test
  void tenantIdCache_positive_evictsEntriesAboveMaxSize() {
    var cache = configuration.tenantIdCache(properties(Duration.ofMinutes(1), 2, Duration.ofMinutes(1), 10));

    for (int i = 0; i < 10; i++) {
      cache.put(KEY + i, VALUE);
    }
    var nativeCache = ((CaffeineCache) cache).getNativeCache();
    nativeCache.cleanUp();

    assertThat(nativeCache.estimatedSize()).isLessThanOrEqualTo(2);
  }

  @Test
  void applicationDescriptorCache_positive_usesItsOwnSettings() {
    var properties = properties(Duration.ofMinutes(1), 10, Duration.ZERO, 10);
    var tenantIdCache = configuration.tenantIdCache(properties);
    var descriptorCache = configuration.applicationDescriptorCache(properties);

    tenantIdCache.put(KEY, VALUE);
    descriptorCache.put(KEY, VALUE);

    assertThat(descriptorCache.getName()).isEqualTo(APPLICATION_DESCRIPTOR);
    assertThat(tenantIdCache.get(KEY, String.class)).isEqualTo(VALUE);
    assertThat(descriptorCache.get(KEY)).isNull();
  }

  @Test
  void accessTokenCache_positive_retainsTokenUntilNearExpiration() {
    when(keycloakProperties.getAuthorizationCacheTtlOffset()).thenReturn(TTL_OFFSET_MILLIS);
    var cache = configuration.accessTokenCache(new AccessTokenExpiry(keycloakProperties));
    var token = accessToken(60);

    cache.put(KEY, token);

    assertThat(cache.getName()).isEqualTo(ACCESS_TOKEN);
    assertThat(cache.get(KEY, AccessTokenResponse.class)).isSameAs(token);
  }

  private static EntitlementCacheProperties properties(Duration tenantTtl, long tenantMaxSize,
    Duration descriptorTtl, long descriptorMaxSize) {
    var properties = new EntitlementCacheProperties();
    properties.setTenantId(settings(tenantTtl, tenantMaxSize));
    properties.setApplicationDescriptor(settings(descriptorTtl, descriptorMaxSize));
    return properties;
  }

  private static CacheSettings settings(Duration ttl, long maxSize) {
    var settings = new CacheSettings();
    settings.setTtl(ttl);
    settings.setMaxSize(maxSize);
    return settings;
  }

  private static AccessTokenResponse accessToken(long expiresInSeconds) {
    var response = new AccessTokenResponse();
    response.setToken("token");
    response.setExpiresIn(expiresInSeconds);
    return response;
  }
}
