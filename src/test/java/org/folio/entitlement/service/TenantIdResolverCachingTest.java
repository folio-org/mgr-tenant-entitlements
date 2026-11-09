package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.configuration.cache.CacheConfiguration.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.util.UUID;
import org.folio.entitlement.configuration.cache.EntitlementCachePolicy;
import org.folio.entitlement.integration.tm.TenantManagerService;
import org.folio.entitlement.integration.tm.model.Tenant;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@UnitTest
@SpringJUnitConfig(TenantIdResolverCachingTest.TestConfig.class)
class TenantIdResolverCachingTest {

  private static final String TENANT_NAME = "diku";
  private static final String ANOTHER_TOKEN = "another-token";
  private static final Tenant TENANT = Tenant.of(UUID.randomUUID(), TENANT_NAME, null);

  @Autowired private TenantIdResolver resolver;
  @Autowired private TenantManagerService tenantManagerService;
  @Autowired private EntitlementCachePolicy cachePolicy;
  @Autowired private CacheManager cacheManager;

  @BeforeEach
  void setUp() {
    reset(tenantManagerService, cachePolicy);
    cacheManager.getCache(TENANT_ID).clear();
  }

  @Test
  void resolve_positive_secondCallIsServedFromCacheRegardlessOfToken() {
    when(cachePolicy.isEnabled()).thenReturn(true);
    when(tenantManagerService.findTenantByName(TENANT_NAME, OKAPI_TOKEN)).thenReturn(TENANT);

    var first = resolver.resolve(TENANT_NAME, OKAPI_TOKEN);
    var second = resolver.resolve(TENANT_NAME, ANOTHER_TOKEN);

    assertThat(first).isSameAs(TENANT);
    assertThat(second).isSameAs(TENANT);
    verify(tenantManagerService).findTenantByName(TENANT_NAME, OKAPI_TOKEN);
  }

  @Test
  void resolve_positive_cachePolicyDisabled_notCached() {
    when(cachePolicy.isEnabled()).thenReturn(false);
    when(tenantManagerService.findTenantByName(TENANT_NAME, OKAPI_TOKEN)).thenReturn(TENANT);

    resolver.resolve(TENANT_NAME, OKAPI_TOKEN);
    resolver.resolve(TENANT_NAME, OKAPI_TOKEN);

    verify(tenantManagerService, times(2)).findTenantByName(TENANT_NAME, OKAPI_TOKEN);
    assertThat(cacheManager.getCache(TENANT_ID).get(TENANT_NAME)).isNull();
  }

  @Test
  void resolve_negative_tenantNotFound_failureIsNotCached() {
    var notFound = new EntityNotFoundException("Tenant is not found by name: " + TENANT_NAME);
    when(cachePolicy.isEnabled()).thenReturn(true);
    when(tenantManagerService.findTenantByName(TENANT_NAME, OKAPI_TOKEN)).thenThrow(notFound).thenReturn(TENANT);

    assertThatThrownBy(() -> resolver.resolve(TENANT_NAME, OKAPI_TOKEN)).isSameAs(notFound);
    var resolved = resolver.resolve(TENANT_NAME, OKAPI_TOKEN);

    assertThat(resolved).isSameAs(TENANT);
    verify(tenantManagerService, times(2)).findTenantByName(TENANT_NAME, OKAPI_TOKEN);
  }

  @Test
  void resolve_positive_tenantNameDiffersFromRequested_notCached() {
    var requestedName = "dik*";
    when(cachePolicy.isEnabled()).thenReturn(true);
    when(tenantManagerService.findTenantByName(requestedName, OKAPI_TOKEN)).thenReturn(TENANT);

    var first = resolver.resolve(requestedName, OKAPI_TOKEN);
    var second = resolver.resolve(requestedName, OKAPI_TOKEN);

    assertThat(first).isSameAs(TENANT);
    assertThat(second).isSameAs(TENANT);
    verify(tenantManagerService, times(2)).findTenantByName(requestedName, OKAPI_TOKEN);
    assertThat(cacheManager.getCache(TENANT_ID).get(requestedName)).isNull();
  }

  @Test
  void evict_positive_nextResolveReloadsTenant() {
    when(cachePolicy.isEnabled()).thenReturn(true);
    when(tenantManagerService.findTenantByName(TENANT_NAME, OKAPI_TOKEN)).thenReturn(TENANT);

    resolver.resolve(TENANT_NAME, OKAPI_TOKEN);
    resolver.evict(TENANT_NAME);
    resolver.resolve(TENANT_NAME, OKAPI_TOKEN);

    verify(tenantManagerService, times(2)).findTenantByName(TENANT_NAME, OKAPI_TOKEN);
  }

  @Configuration
  @EnableCaching
  static class TestConfig {

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager(TENANT_ID);
    }

    @Bean
    TenantManagerService tenantManagerService() {
      return mock(TenantManagerService.class);
    }

    @Bean("entitlementCachePolicy")
    EntitlementCachePolicy entitlementCachePolicy() {
      return mock(EntitlementCachePolicy.class);
    }

    @Bean
    TenantIdResolver tenantIdResolver(TenantManagerService tenantManagerService) {
      return new TenantIdResolver(tenantManagerService);
    }
  }
}
