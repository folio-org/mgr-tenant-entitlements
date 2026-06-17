# Server-side cache for `getModuleEntitlements` in mgr-tenant-entitlements

- **Date:** 2026-06-16 (revised 2026-06-17: simplified to in-process eviction — mte is single-instance)
- **Status:** Approved (design)
- **Repo:** `mgr-tenant-entitlements` (mte)
- **Scope:** mte only — transparent to `folio-module-sidecar` and `mgr-applications`

## Problem

`folio-module-sidecar` reads `GET /entitlements/modules/{moduleId}` from mte to learn which
tenants have a given module enabled. The sidecar calls it at startup, on a scheduled refresh
(default cron `0 */5 * * * ?`), and on `TenantNotEnabledException` error-recovery — the last of
which can repeat per request for a not-yet-enabled tenant. Each call runs an uncached
`@Transactional(readOnly = true)` query (`EntitlementModuleRepository.findAllByModuleId`)
straight against the DB. As tenant/module counts grow, this puts avoidable read load on mte's
database.

Goal: add a server-side cache in mte on the by-module-id read so repeated reads from the sidecar
do not hit the DB.

## Deployment assumption

**mte runs as a single instance** (it cannot be horizontally scaled). This removes any
cross-replica cache-coherence problem: an in-process cache with in-process eviction is fully
correct. No distributed cache and no Kafka-based invalidation are needed.

## Non-goals

- No changes to `folio-module-sidecar` or `mgr-applications`. They remain the load drivers; the
  fix lives where the DB hit happens. The cache is transparent to callers.
- The by-tenant / CQL read (`GET /entitlements`, `EntitlementService.findByQueryOrTenantName`) is
  **not** cached here. Different query path, explicitly out of scope.
- No distributed cache, no Kafka consumer. (mte stays producer-only.)

## Current state (verified)

- **Stack:** Spring Boot 4.1.0, Java 21. `spring-boot-starter-cache` + Caffeine 3.1.8 already on
  the classpath.
- **Target read path:**
  - `EntitlementModuleController.getModuleEntitlements(moduleId, limit, offset)` →
  - `EntitlementModuleService.getModuleEntitlements(moduleId, limit, offset)` →
  - `EntitlementModuleRepository.findAllByModuleId(moduleId, OffsetRequest.of(offset, limit, SORT_BY_TENANT))`.
  - REST path: `/entitlements/modules/{moduleId}`. Defaults: `limit=10` (`@Max(500)`), `offset=0`.
    Sort: `EntitlementModuleEntity.SORT_BY_TENANT` (tenantId ASC).
  - Response DTO `Entitlements` is a **mutable** generated POJO — must not be shared from the cache.
- **Existing caching:** only an `access-token` Caffeine cache in `CacheConfiguration`. That class
  carries `@EnableCaching` **and** is `@ConditionalOnProperty("application.keycloak.enabled")`.
  Consequence: when `keycloak.enabled=false` (the default & IT profiles) the whole class is
  skipped, `@EnableCaching` is never applied, and any `@Cacheable` is **silently ignored**.
- **Writes to `entitlement_module`** go only through `EntitlementModuleService`
  (`save`, `saveAll`, two `deleteModuleEntitlement` overloads, `deleteAll`); all under a
  class-level `@Transactional`. No code writes the table outside this service. This is the single
  choke point for cache eviction.
- mte publishes `${ENV}.entitlement` events on entitlement changes, but we **do not** rely on them
  for invalidation (single instance → in-process eviction suffices).

## Chosen approach

Cache the full per-module entitlement list keyed on `moduleId`, paginate in memory, and invalidate
in-process by evicting the cache whenever the entitlement table is written. `expireAfterWrite` is a
backstop only.

### 1. Cache shape — cache-by-moduleId, paginate in memory

- New bean `ModuleEntitlementsCacheProvider` holds the cached read in its own bean so Spring's
  cache proxy is honored (a self-invocation from `getModuleEntitlements` into a `@Cacheable` method
  on the same bean would bypass it). Mirrors `mgr-applications`' `ModuleBootstrapDataProvider`.
  - `@Cacheable(cacheNames = "module-entitlements", cacheManager = "moduleEntitlementsCacheManager", key = "#moduleId")`
    `List<Entitlement> getByModuleId(String moduleId)` — queries the full result set sorted by
    tenant (no paging), maps to `List<Entitlement>`, returns an **unmodifiable copy**
    (`List.copyOf(...)`).
- New repository method `List<EntitlementModuleEntity> findAllByModuleId(String moduleId, Sort sort)`.
- `EntitlementModuleService.getModuleEntitlements(moduleId, limit, offset)` is rewritten to fetch
  the cached list and slice `[offset, offset+limit)` with bounds handling, building a fresh
  `Entitlements{totalRecords = list.size(), entitlements = new copy of slice}`. The cached list is
  never exposed. Default `limit=10`/`offset=0` preserved.

Rationale for not keying on `(moduleId, limit, offset)`: it explodes cardinality and makes by-key
eviction harder. Per-module result sets are small, so caching the whole list and slicing is cheap.

### 2. Cache infrastructure — fix gating, add a dedicated manager

- New always-on `ModuleEntitlementsCacheConfiguration` carries `@EnableCaching` (so the cache works
  when keycloak is disabled). Remove `@EnableCaching` from `CacheConfiguration`.
- `moduleEntitlementsCacheManager` bean: a `CaffeineCacheManager` (`maximumSize` + `expireAfterWrite`
  + debug removal logging) when `application.module-entitlements-cache.enabled=true` (default), and
  a `NoOpCacheManager` under the **same bean name** when `false`, so
  `cacheManager = "moduleEntitlementsCacheManager"` always resolves.
- Coexistence with the keycloak `accessTokenCacheManager`: two managers means no implicit default, so
  both `@Cacheable`/`@CacheEvict` sites name their manager — the new ones use
  `"moduleEntitlementsCacheManager"`; `KeycloakCacheableService` gains
  `cacheManager = "accessTokenCacheManager"`.

### 3. Invalidation — in-process eviction + TTL backstop

- Annotate each `EntitlementModuleService` write method
  (`save`, `saveAll`, both `deleteModuleEntitlement` overloads, `deleteAll`) with
  `@CacheEvict(cacheNames = "module-entitlements", cacheManager = "moduleEntitlementsCacheManager", allEntries = true)`.
  - `allEntries = true` is chosen for simplicity over per-key eviction: writes are infrequent admin
    operations and the cache is small, so clearing it wholesale on any entitlement change is cheap
    and removes all per-key/list-handling logic. The sidecar repopulates on its next read.
  - These methods are invoked from other beans (flow stages), not via self-invocation, so the cache
    proxy applies.
  - Default `beforeInvocation = false`: eviction happens only after the write returns successfully,
    so a rolled-back write does not evict.
- Eviction is in-process and correct because there is exactly one mte instance. REVOKE clears the
  cache immediately, so the sidecar never sees a revoked entitlement from a stale entry (beyond the
  brief in-transaction window noted in Risks).
- **TTL backstop** (`expireAfterWrite`, default 30m) bounds any residual staleness (e.g. the tiny
  window if an eviction fires just before the surrounding transaction commits).

### 4. Configuration

```yaml
application:
  module-entitlements-cache:
    enabled: ${MODULE_ENTITLEMENTS_CACHE_ENABLED:true}
    max-size: ${MODULE_ENTITLEMENTS_CACHE_MAX_SIZE:1000}
    ttl: ${MODULE_ENTITLEMENTS_CACHE_TTL:30m}
```

Backed by `@ConfigurationProperties("application.module-entitlements-cache")`
(`ModuleEntitlementsCacheProperties`: `long maxSize`, `Duration ttl`). No Kafka/consumer config.

### 5. Error handling

- Provider/cache failure → read falls through to the DB; a cache problem never breaks a read.
- `enabled=false` → `NoOpCacheManager`; behavior identical to today (every read hits the DB).
- Cold start → empty cache; first read per module populates from DB.

### 6. Testing

- **Unit (`EntitlementModuleService`):** in-memory pagination — empty, single page, partial last
  page, `offset` beyond size, default `limit=10`; returns a fresh `Entitlements` and list.
- **Unit (`ModuleEntitlementsCacheProvider`, Spring slice):** returns an unmodifiable list; a second
  call with the same `moduleId` does not re-query the repository.
- **Cache eviction (Spring slice):** populate via the provider, call an `EntitlementModuleService`
  write method, assert the cache entry is gone.
- **Config (`ApplicationContextRunner`):** enabled → `CaffeineCacheManager`; disabled →
  `NoOpCacheManager`.
- **Integration (`BaseIntegrationTest`, `it` profile = keycloak off):** prove caching is active
  without keycloak (the gating fix) and that a write evicts — synchronous, no Kafka.
- **Regression:** the access-token `@Cacheable` still works after moving `@EnableCaching` and adding
  the explicit `cacheManager` qualifier.

### Files

All in `mgr-tenant-entitlements`:

- **New**
  - `configuration/cache/ModuleEntitlementsCacheConfiguration.java` (always-on `@EnableCaching`,
    `moduleEntitlementsCacheManager` + NoOp fallback).
  - `configuration/cache/ModuleEntitlementsCacheProperties.java`.
  - `service/ModuleEntitlementsCacheProvider.java` (`@Cacheable` read).
- **Modified**
  - `configuration/cache/CacheConfiguration.java` — remove `@EnableCaching`.
  - `integration/keycloak/KeycloakCacheableService.java` — add `cacheManager = "accessTokenCacheManager"`.
  - `service/EntitlementModuleService.java` — `getModuleEntitlements` delegates + paginates; write
    methods get `@CacheEvict(allEntries = true)`.
  - `repository/EntitlementModuleRepository.java` — add `findAllByModuleId(String, Sort)`.
  - `src/main/resources/application.yml` — cache config block.
  - `README.md`.

## Risks and mitigations

- **Mutable DTO leak** → cache an unmodifiable `List<Entitlement>`, return fresh copies/slices.
- **Self-invocation proxy bypass** → cached read lives in a dedicated `ModuleEntitlementsCacheProvider`
  bean; eviction lives on the externally-invoked service write methods.
- **Silent no-op when keycloak disabled** → move `@EnableCaching` to an always-on config; the IT
  (keycloak off) asserts caching is active.
- **Evict-before-commit window** → `@CacheEvict` runs after a successful write but possibly just
  before the surrounding transaction commits; on a single instance with infrequent writes this is a
  sub-second window, bounded by the TTL backstop. Acceptable; not worth an `afterCommit` hook.
- **Future multi-instance** → if mte ever becomes multi-replica, in-process eviction is no longer
  sufficient and a broadcast invalidation (e.g. consuming `${ENV}.entitlement`) would be required.
  Documented here so the assumption is explicit.
