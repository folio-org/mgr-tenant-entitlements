package org.folio.entitlement.service;

import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.configuration.cache.ModuleEntitlementsCacheConfiguration.MODULE_ENTITLEMENTS_CACHE;
import static org.folio.entitlement.domain.entity.key.EntitlementModuleEntity.SORT_BY_TENANT;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.mapper.EntitlementModuleMapper;
import org.folio.entitlement.repository.EntitlementModuleRepository;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Single DB-touching, cached entry point for per-module entitlements. Lives in its own bean (not on
 * {@link EntitlementModuleService}) so the cache proxy is honored — a self-invocation from the
 * service would bypass it. Caches the full per-module list keyed on {@code moduleId}; callers
 * paginate the returned immutable list in memory.
 *
 * <p>The cache-manager field is named after the {@code moduleEntitlementsCacheManager} bean so it
 * resolves unambiguously even when the keycloak {@code accessTokenCacheManager} is also present.
 */
@Component
@RequiredArgsConstructor
public class ModuleEntitlementsCacheProvider {

  private final EntitlementModuleRepository repository;
  private final EntitlementModuleMapper mapper;
  private final CacheManager moduleEntitlementsCacheManager;

  @Cacheable(cacheNames = MODULE_ENTITLEMENTS_CACHE, cacheManager = "moduleEntitlementsCacheManager",
    key = "#moduleId", sync = true)
  @Transactional(readOnly = true)
  public List<Entitlement> getByModuleId(String moduleId) {
    var entities = repository.findAllByModuleId(moduleId, SORT_BY_TENANT);
    return List.copyOf(mapItems(entities, mapper::map));
  }

  /**
   * Evicts a module's cached entry. When invoked inside a transaction the eviction is deferred until
   * after commit, so a concurrent read that misses cannot repopulate the entry with the writer's
   * pre-commit (stale) snapshot; with no active transaction it evicts immediately. On rollback the
   * entry is left untouched — it still matches the unchanged database row.
   */
  public void evict(String moduleId) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          evictNow(moduleId);
        }
      });
    } else {
      evictNow(moduleId);
    }
  }

  private void evictNow(String moduleId) {
    var cache = moduleEntitlementsCacheManager.getCache(MODULE_ENTITLEMENTS_CACHE);
    if (cache != null) {
      cache.evict(moduleId);
    }
  }
}
