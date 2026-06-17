package org.folio.entitlement.service;

import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.configuration.cache.ModuleEntitlementsCacheConfiguration.MODULE_ENTITLEMENTS_CACHE;
import static org.folio.entitlement.domain.entity.key.EntitlementModuleEntity.SORT_BY_TENANT;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.mapper.EntitlementModuleMapper;
import org.folio.entitlement.repository.EntitlementModuleRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single DB-touching, cached entry point for per-module entitlements. Lives in its own bean (not on
 * {@link EntitlementModuleService}) so the cache proxy is honored — a self-invocation from the
 * service would bypass it. Caches the full per-module list keyed on {@code moduleId}; callers
 * paginate the returned immutable list in memory.
 */
@Component
@RequiredArgsConstructor
public class ModuleEntitlementsCacheProvider {

  private final EntitlementModuleRepository repository;
  private final EntitlementModuleMapper mapper;

  @Cacheable(cacheNames = MODULE_ENTITLEMENTS_CACHE, cacheManager = "moduleEntitlementsCacheManager",
    key = "#moduleId", sync = true)
  @Transactional(readOnly = true)
  public List<Entitlement> getByModuleId(String moduleId) {
    var entities = repository.findAllByModuleId(moduleId, SORT_BY_TENANT);
    return List.copyOf(mapItems(entities, mapper::map));
  }

  @CacheEvict(cacheNames = MODULE_ENTITLEMENTS_CACHE, cacheManager = "moduleEntitlementsCacheManager",
    key = "#moduleId")
  public void evict(String moduleId) {
    // body intentionally empty: eviction is performed by @CacheEvict
  }
}
