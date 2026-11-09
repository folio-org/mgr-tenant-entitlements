package org.folio.entitlement.service;

import static org.folio.entitlement.configuration.cache.CacheConfiguration.TENANT_ID;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.tm.TenantManagerService;
import org.folio.entitlement.integration.tm.model.Tenant;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Resolves tenants by name for the entitled applications endpoint, caching the result.
 *
 * <p>Tenant name never changes, but a deleted tenant can be re-created under the same name with another id,
 * so entries expire and can be evicted. Tenants whose name differs from the requested one (e.g. matched by a
 * wildcard) and failed lookups are never cached.</p>
 */
@Service
@RequiredArgsConstructor
public class TenantIdResolver {

  private final TenantManagerService tenantManagerService;

  @Cacheable(cacheNames = TENANT_ID, key = "#tenantName", condition = "@entitlementCachePolicy.enabled",
    unless = "#result == null or #result.name != #tenantName")
  public Tenant resolve(String tenantName, String authToken) {
    return tenantManagerService.findTenantByName(tenantName, authToken);
  }

  @CacheEvict(cacheNames = TENANT_ID, key = "#tenantName")
  public void evict(String tenantName) {
    // eviction is performed by the cache interceptor
  }
}
