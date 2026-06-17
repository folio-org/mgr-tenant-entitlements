package org.folio.entitlement.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toUnmodifiableList;
import static org.folio.entitlement.configuration.cache.ModuleEntitlementsCacheConfiguration.MODULE_ENTITLEMENTS_CACHE;
import static org.folio.entitlement.domain.entity.key.EntitlementModuleEntity.SORT_BY_TENANT;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.mapper.EntitlementModuleMapper;
import org.folio.entitlement.repository.EntitlementModuleRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps the module-entitlements cache warm with a single batched query: loads every
 * {@code entitlement_module} row once, groups by moduleId, and (re)populates the cache. Runs at
 * startup (no cold-start storm across the many unique module IDs) and on a fixed delay (keeps entries
 * warm and backstops missed evictions). One DB round-trip regardless of module count.
 *
 * <p>The {@code moduleEntitlementsCacheManager} field name selects the cache manager by name, so it
 * resolves unambiguously even when the keycloak {@code accessTokenCacheManager} is also present.
 */
@Log4j2
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.module-entitlements-cache.enabled", havingValue = "true",
  matchIfMissing = true)
public class ModuleEntitlementsCacheWarmer {

  private final EntitlementModuleRepository repository;
  private final EntitlementModuleMapper mapper;
  private final CacheManager moduleEntitlementsCacheManager;

  @Scheduled(initialDelayString = "0s",
    fixedDelayString = "${application.module-entitlements-cache.refresh-interval:1h}")
  @Transactional(readOnly = true)
  public void warmUp() {
    var cache = moduleEntitlementsCacheManager.getCache(MODULE_ENTITLEMENTS_CACHE);
    if (cache == null) {
      return;
    }
    var byModule = repository.findAll(SORT_BY_TENANT).stream()
      .collect(groupingBy(EntitlementModuleEntity::getModuleId, mapping(mapper::map, toUnmodifiableList())));
    byModule.forEach(cache::put);
    log.info("Module-entitlements cache warmed: modules={}", byModule.size());
  }
}
