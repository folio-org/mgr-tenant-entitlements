# Server-side cache for `getModuleEntitlements` in mgr-tenant-entitlements

- **Date:** 2026-06-16
- **Status:** Approved (design)
- **Repo:** `mgr-tenant-entitlements` (mte)
- **Scope:** mte only — transparent to `folio-module-sidecar` and `mgr-applications`

## Problem

`folio-module-sidecar` reads `GET /entitlements/modules/{moduleId}` from mte to learn which
tenants have a given module enabled. The sidecar calls it at startup, on a scheduled refresh
(default cron `0 */5 * * * ?`), and on `TenantNotEnabledException` error-recovery — the last of
which can repeat per request for a not-yet-enabled tenant. Each call runs an uncached
`@Transactional(readOnly = true)` query (`EntitlementModuleRepository.findAllByModuleId`)
straight against the DB. With multiple sidecar instances and growing tenant/module counts, this
puts avoidable read load on mte's database.

Goal: add a server-side cache in mte on the by-module-id read so repeated reads from the sidecar
do not hit the DB, while staying correct across mte replicas.

## Non-goals

- No changes to `folio-module-sidecar` or `mgr-applications`. They remain the load drivers; the
  fix lives where the DB hit happens. The cache is transparent to callers.
- The by-tenant / CQL read (`GET /entitlements`, `EntitlementService.findByQueryOrTenantName`) is
  **not** cached here. It is a different query path and was explicitly out of scope.
- No distributed cache (Redis/Hazelcast). Each replica keeps a local Caffeine cache invalidated
  by a broadcast Kafka event.

## Current state (verified)

- **Stack:** Spring Boot 4.1.0, Java 21. `spring-boot-starter-cache` + Caffeine 3.1.8 already on
  the classpath.
- **Target read path:**
  - `EntitlementModuleController.getModuleEntitlements(moduleId, limit, offset)` →
  - `EntitlementModuleService.getModuleEntitlements(moduleId, limit, offset)` →
  - `EntitlementModuleRepository.findAllByModuleId(moduleId, OffsetRequest.of(offset, limit, SORT_BY_TENANT))`.
  - REST path constant: `/entitlements/modules/{moduleId}`. Defaults: `limit=10` (`@Max(500)`),
    `offset=0`. Sort: `EntitlementModuleEntity.SORT_BY_TENANT` (tenantId ASC).
  - Response DTO `Entitlements` is a **mutable** generated POJO (`totalRecords`, `entitlements`
    list with setters) — must not be shared from the cache.
- **Existing caching:** only an `access-token` Caffeine cache in
  `org.folio.entitlement.configuration.cache.CacheConfiguration`. That class carries
  `@EnableCaching` **and** is annotated `@ConditionalOnProperty("application.keycloak.enabled")`.
  Consequence: when `keycloak.enabled=false` (the default and IT profiles), the whole class is
  skipped, `@EnableCaching` is never applied, and any `@Cacheable` is **silently ignored**.
- **Writes to `entitlement_module`** go only through `EntitlementModuleService`
  (`save`, `saveAll`, two `deleteModuleEntitlement` overloads, `deleteAll`); all under a
  class-level `@Transactional`. No code writes the table outside this service. No
  `TransactionSynchronization`/`afterCommit` pattern exists anywhere in the repo.
- **Kafka:** mte is **producer-only** today (no consumer, no `@KafkaListener`). On every
  DB-mutating module change it publishes a per-module `EntitlementEvent{type, moduleId,
  tenantName, tenantId}` to the env-wide topic `${ENV}.entitlement` (key `tenantName_moduleId`):
  - `FolioModuleEventPublisher.execute()` — entitle (new id), revoke (`moduleDescriptor == null`),
    upgrade swap (REVOKE old id **and** UPGRADE/ENTITLE new id); emits nothing when the module is
    unchanged (and the DB is likewise unchanged).
  - `FolioModuleEventPublisher.cancel()` — REVOKE on ENTITLE rollback.
  - `OkapiModulesEventPublisher` — same per-module event on the okapi path.
  - Producer config: `acks=all`, `enable.idempotence=true`, blocking send. Env topic name via
    `org.folio.integration.kafka.producer.KafkaUtils.getEnvTopicName`.
- **Deployment:** stateless, multi-replica in Kubernetes. So in-process eviction alone is
  insufficient; reads are load-balanced across replicas.

## Chosen approach

Cache the full per-module entitlement list keyed on `moduleId`, paginate in memory, and
invalidate across replicas via a new Kafka broadcast consumer on `${ENV}.entitlement`, with a
TTL backstop. This is the only option that is both event-driven and correct across replicas, and
it reuses the invalidation pattern already proven by `mgr-applications`' `module-bootstrap` cache
(per-instance broadcast consumer group + TTL backstop).

### 1. Cache shape — cache-by-moduleId, paginate in memory

- New bean `ModuleEntitlementsCacheProvider` holds the cached read. It is a **separate bean** so
  Spring's cache proxy is honored (a self-invocation from `getModuleEntitlements` into a
  `@Cacheable` method on the same bean would bypass the proxy). This mirrors
  `mgr-applications`' `ModuleBootstrapDataProvider`.
  - `@Cacheable(cacheNames = "module-entitlements", cacheManager = "moduleEntitlementsCacheManager", key = "#moduleId")`
    `List<Entitlement> getByModuleId(String moduleId)`.
  - Implementation: query the **full** result set sorted by tenant (no paging) and map to
    `List<Entitlement>`; return an **unmodifiable copy** (`List.copyOf(...)`) so the cached value
    cannot be mutated by callers.
  - `@CacheEvict(cacheNames = "module-entitlements", cacheManager = "moduleEntitlementsCacheManager", key = "#moduleId")`
    `void evict(String moduleId)`.
  - Dependencies: `EntitlementModuleRepository` + `EntitlementModuleMapper`.
- New repository method: `List<EntitlementModuleEntity> findAllByModuleId(String moduleId, Sort sort)`
  on `EntitlementModuleRepository` (returns all rows for the module; reuse
  `EntitlementModuleEntity.SORT_BY_TENANT`).
- `EntitlementModuleService.getModuleEntitlements(moduleId, limit, offset)` is rewritten to:
  1. `var all = cacheProvider.getByModuleId(moduleId);`
  2. compute bounds: `from = clamp(offset, 0, all.size())`, `to = clamp(offset + limit, from, all.size())`;
  3. return `new Entitlements().totalRecords(all.size()).entitlements(new ArrayList<>(all.subList(from, to)))`.
  - A fresh `Entitlements` and a fresh list are built per call; the cached list is never exposed.
  - Default `limit=10`/`offset=0` semantics are preserved (controller still supplies them).

Rationale for not keying on `(moduleId, limit, offset)`: it explodes cache cardinality and makes
by-`moduleId` eviction impossible. Per-module result sets are small (bounded by the number of
tenant×application rows that reference the module), so caching the whole list and slicing is
cheap and makes eviction a single key.

### 2. Cache infrastructure — fix gating, add a dedicated manager

- Introduce `ModuleEntitlementsCacheConfiguration` (always-on, **not** gated on keycloak) that
  carries `@EnableCaching`. Remove `@EnableCaching` from `CacheConfiguration` so `@EnableCaching`
  has a single, unconditional home. (When keycloak is enabled both configs are active; with
  `@EnableCaching` only on the always-on one there is no duplication.)
- `moduleEntitlementsCacheManager` bean:
  - When `application.module-entitlements-cache.enabled=true` (default): a `CaffeineCacheManager`
    for the single cache `module-entitlements`, built from a Caffeine spec with
    `maximumSize = max-size`, `expireAfterWrite = ttl`, and a debug-level removal listener (match
    the style of the existing `accessTokenCaffeine`).
  - When `false`: the **same bean name** resolves to a `NoOpCacheManager`, so the
    `cacheManager = "moduleEntitlementsCacheManager"` reference always resolves. (Mirrors
    `mgr-applications`' FAR-mode `NoOpCacheManager` swap.) Implement as two
    `@ConditionalOnProperty` beans (`havingValue="true", matchIfMissing=true` vs
    `havingValue="false"`).
- Coexistence with the existing `accessTokenCacheManager`: with two `CacheManager` beans there is
  no implicit default, so **both** `@Cacheable`/`@CacheEvict` sites must name their manager:
  - new provider methods use `cacheManager = "moduleEntitlementsCacheManager"`;
  - `KeycloakCacheableService`'s existing access-token `@Cacheable` gains
    `cacheManager = "accessTokenCacheManager"`.
  - This is a small change but must be regression-tested (access-token caching must still work).

### 3. Invalidation — Kafka broadcast consumer + TTL backstop

- Add mte's first Kafka consumer:
  - `ConcurrentKafkaListenerContainerFactory` wired from the existing
    `spring.kafka.bootstrap-servers`, JSON deserialization of `EntitlementEvent`.
  - `@KafkaListener` (e.g. `EntitlementCacheInvalidationListener`) on topic
    `${ENV}.entitlement` (resolve the env prefix the same way producers do, via
    `KafkaUtils.getEnvTopicName("entitlement")`).
  - **Per-instance broadcast group:** group id
    `<ENV>-mgr-tenant-entitlements-module-entitlements-cache-<UUID>` with `auto-offset-reset=latest`
    and ephemeral offsets (no durable commits), so every replica receives every event. Mirror
    `mgr-applications`' `KAFKA_BOOTSTRAP_CACHE_GROUP_ID` UUID-suffix approach. Expose the base
    group id via `KAFKA_MODULE_ENTITLEMENTS_CACHE_GROUP_ID`.
  - On each event: `cacheProvider.evict(event.getModuleId())`. ENTITLE / UPGRADE / REVOKE all
    evict identically (the entry is stale regardless of direction). The publisher already emits a
    per-module event for every DB-mutating change including the upgrade module-id swap and ENTITLE
    rollback, so keying eviction on `moduleId` is complete.
  - Start the listener only when caching is enabled
    (`application.module-entitlements-cache.enabled=true`).
- **TTL backstop** (`expireAfterWrite`, default 30m) covers the rare window where the DB commits
  but the process dies before the Kafka publish (the publish is a separate flow stage, not in the
  DB transaction). No in-process `afterCommit` eviction is added: the broadcast consumer evicts
  all replicas (including the writer) uniformly within Kafka latency, so a bespoke `afterCommit`
  pattern would add complexity without improving cross-replica correctness.

### 4. Configuration

New keys under the existing `application.*` convention (kebab-case YAML, env-var overrides):

```yaml
application:
  module-entitlements-cache:
    enabled: ${MODULE_ENTITLEMENTS_CACHE_ENABLED:true}
    max-size: ${MODULE_ENTITLEMENTS_CACHE_MAX_SIZE:1000}
    ttl: ${MODULE_ENTITLEMENTS_CACHE_TTL:30m}
```

- Backed by `@ConfigurationProperties("application.module-entitlements-cache")`
  (`ModuleEntitlementsCacheProperties`: `boolean enabled`, `long maxSize`, `Duration ttl`).
- Kafka consumer group base id via `KAFKA_MODULE_ENTITLEMENTS_CACHE_GROUP_ID`
  (default `${ENV}-mgr-tenant-entitlements-module-entitlements-cache`), UUID suffix appended per
  instance at startup.
- Defaults rationale: `max-size=1000` comfortably exceeds typical distinct-module counts (matches
  `mgr-applications`' bootstrap cache); `ttl=30m` is a pure safety net since the broadcast
  consumer is the primary freshness mechanism.

### 5. Error handling

- Provider/cache failure → read falls through to the DB; a cache problem never breaks a read.
- Consumer lag/outage → bounded staleness equal to the TTL.
- `enabled=false` → `NoOpCacheManager`, listener not started; behavior identical to today.
- Cold start → empty cache; first read per module populates from DB.

### 6. Testing

- **Unit (`EntitlementModuleService`):** in-memory pagination — empty list, single page,
  `offset` beyond size, default `limit=10`, partial last page; verify a fresh `Entitlements` and
  list are returned (cached list not mutated).
- **Unit (`ModuleEntitlementsCacheProvider`):** returns an unmodifiable list; a second call with
  the same `moduleId` does not re-query the repository (verify single repo invocation); `evict`
  clears the entry.
- **Unit (listener):** an `EntitlementEvent` triggers `evict(moduleId)`.
- **Integration (Testcontainers Kafka + Postgres):** entitle a module → read populates cache →
  revoke → consumed `${ENV}.entitlement` event evicts → re-read reflects the change. The IT
  profile **must set `application.module-entitlements-cache.enabled=true`** (caching is now
  independent of keycloak; the default IT profile has keycloak disabled).
- **Regression:** the access-token `@Cacheable` still works after moving `@EnableCaching` and
  adding the explicit `cacheManager` qualifier.

### Files

All in `mgr-tenant-entitlements`:

- **New**
  - `configuration/cache/ModuleEntitlementsCacheConfiguration.java` (always-on `@EnableCaching`,
    `moduleEntitlementsCacheManager` + NoOp fallback).
  - `configuration/cache/ModuleEntitlementsCacheProperties.java`.
  - `service/ModuleEntitlementsCacheProvider.java` (`@Cacheable` read + `@CacheEvict`).
  - Kafka consumer: `EntitlementCacheInvalidationListener.java` + listener-container-factory
    config (e.g. `configuration/kafka/EntitlementCacheConsumerConfiguration.java`).
- **Modified**
  - `configuration/cache/CacheConfiguration.java` — remove `@EnableCaching`; keep the
    keycloak-gated `accessTokenCacheManager`.
  - `integration/keycloak/KeycloakCacheableService.java` — add
    `cacheManager = "accessTokenCacheManager"` to its `@Cacheable`.
  - `service/EntitlementModuleService.java` — `getModuleEntitlements` delegates to the provider
    and paginates in memory.
  - `repository/EntitlementModuleRepository.java` — add `findAllByModuleId(String, Sort)`.
  - `src/main/resources/application.yml` (+ IT profile) — cache + consumer config.
  - `README.md` — document the cache and its config/env vars.

## Open verification items for implementation

- Confirm the exact env-prefix resolution used by `KafkaUtils.getEnvTopicName` so the consumer
  subscribes to the identical topic name the publisher writes to.
- Confirm `EntitlementEvent` is JSON-deserializable by a Spring Kafka consumer as-is (field
  names/visibility), or add a consumer-side payload type if needed.
- Confirm the IT Kafka harness already spins up a broker usable by the new consumer.

## Risks and mitigations

- **Mutable DTO leak** → cache an unmodifiable `List<Entitlement>` and return fresh copies/slices.
- **Self-invocation proxy bypass** → cached read lives in a dedicated `ModuleEntitlementsCacheProvider` bean.
- **Silent no-op when keycloak disabled** → move `@EnableCaching` to an always-on config; assert
  in IT (keycloak off) that caching is active.
- **Multi-replica staleness** → broadcast consumer evicts all replicas; TTL backstop for missed
  events.
- **DB-commit / Kafka-publish dual write** → TTL backstop bounds the worst case; consistent with
  how the rest of the system already relies on `${ENV}.entitlement`.
- **Consumer-group proliferation** (one ephemeral group per restart) → accepted; same trade-off
  `mgr-applications` already makes.
