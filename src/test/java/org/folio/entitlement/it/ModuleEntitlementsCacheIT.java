package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.configuration.cache.ModuleEntitlementsCacheConfiguration.MODULE_ENTITLEMENTS_CACHE;

import java.util.UUID;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.domain.entity.key.EntitlementModuleKey;
import org.folio.entitlement.repository.EntitlementModuleRepository;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.ModuleEntitlementsCacheProvider;
import org.folio.entitlement.service.ModuleEntitlementsCacheWarmer;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@IntegrationTest
class ModuleEntitlementsCacheIT extends BaseIntegrationTest {

  private static final String MODULE_ID = "cache-it-module-1.0.0";
  private static final String APPLICATION_ID = "cache-it-app-1.0.0";
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000aa1");

  @Autowired private CacheManager cacheManager;
  @Autowired private EntitlementModuleRepository repository;
  @Autowired private ModuleEntitlementsCacheProvider cacheProvider;
  @Autowired private ModuleEntitlementsCacheWarmer warmer;
  @Autowired private EntitlementModuleService entitlementModuleService;

  @AfterEach
  void cleanUp() {
    var key = EntitlementModuleKey.of(MODULE_ID, TENANT_ID, APPLICATION_ID);
    if (repository.existsById(key)) {
      repository.deleteById(key);
    }
    var cache = cache();
    if (cache != null) {
      cache.clear();
    }
  }

  @Test
  void cacheIsWarmedReadAndEvictedOnWrite() {
    var entity = new EntitlementModuleEntity();
    entity.setModuleId(MODULE_ID);
    entity.setTenantId(TENANT_ID);
    entity.setApplicationId(APPLICATION_ID);
    repository.save(entity);

    // the warmer populates the cache from a single batched query — and caching is active with
    // keycloak disabled (it profile), proving the gating fix and the by-name cache-manager wiring
    warmer.warmUp();
    assertThat(cache().get(MODULE_ID)).as("entry warmed").isNotNull();

    // reads are served from the warmed cache (pagination wrapper works end to end)
    assertThat(cacheProvider.getByModuleId(MODULE_ID)).hasSize(1);

    // a write to the entitlement table evicts that module's entry in-process
    entitlementModuleService.deleteModuleEntitlement(MODULE_ID, TENANT_ID, APPLICATION_ID);
    assertThat(cache().get(MODULE_ID)).as("entry evicted after write").isNull();
  }

  private Cache cache() {
    return cacheManager.getCache(MODULE_ENTITLEMENTS_CACHE);
  }
}
