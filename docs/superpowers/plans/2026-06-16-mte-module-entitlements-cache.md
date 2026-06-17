# Module-Entitlements Server-Side Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cache `EntitlementModuleService.getModuleEntitlements(moduleId, …)` in mgr-tenant-entitlements so repeated reads from the sidecar don't hit the DB, invalidated in-process whenever entitlements change.

**Architecture:** mte is single-instance, so an in-process cache with in-process eviction is fully correct. Cache the full per-module entitlement list keyed on `moduleId` in a dedicated Caffeine cache (in its own bean so the cache proxy isn't bypassed), read with `sync = true` to avoid a stampede across many sidecars; paginate in memory. Every write to `entitlement_module` (all of which go through `EntitlementModuleService`) evicts exactly the affected module IDs via `cacheProvider.evict(moduleId)` — per-key, so unrelated modules stay cached; `expireAfterWrite` is a backstop. `@EnableCaching` moves to an always-on config so the cache works regardless of `application.keycloak.enabled`.

**Tech Stack:** Spring Boot 4.1.0, Java 21, Spring Cache + Caffeine 3.1.8, JUnit 5 + Mockito + AssertJ, Testcontainers (Postgres) + embedded Kafka (provided by the IT base; not used directly).

**Design spec:** `docs/superpowers/specs/2026-06-16-mte-module-entitlements-cache-design.md`

## Conventions for this repo (read before running anything)

- **JDK:** `mvn` here defaults to Java 26 and silently breaks Lombok. Export `JAVA_HOME` to a Java 21 JDK before every Maven command.
- **Unit tests** are `@UnitTest` (surefire): `mvn test -Dtest=<ClassName>`.
- **Integration tests** are `@IntegrationTest` (failsafe). `failsafe:integration-test` does NOT run `test-compile`, so always prepend it. During TDD add `-Dcheckstyle.skip`:
  `mvn test-compile failsafe:integration-test -Dit.test='**/<ClassName>.java' -Dcheckstyle.skip`

---

## File Structure

**New files (under `src/main/java/org/folio/entitlement`):**
- `configuration/cache/ModuleEntitlementsCacheProperties.java` — `@ConfigurationProperties` (maxSize, ttl).
- `configuration/cache/ModuleEntitlementsCacheConfiguration.java` — always-on `@EnableCaching`, `moduleEntitlementsCacheManager` (Caffeine when enabled / NoOp when disabled), cache-name constant.
- `service/ModuleEntitlementsCacheProvider.java` — the `@Cacheable` read (`sync = true`) + `@CacheEvict` per-key evict in a dedicated bean.

**Modified files:**
- `configuration/cache/CacheConfiguration.java` — remove `@EnableCaching`.
- `integration/keycloak/KeycloakCacheableService.java` — add `cacheManager = "accessTokenCacheManager"`.
- `service/EntitlementModuleService.java` — `getModuleEntitlements` delegates + paginates; write methods call `cacheProvider.evict(...)` for the affected module IDs.
- `repository/EntitlementModuleRepository.java` — add `findAllByModuleId(String, Sort)`.
- `src/main/resources/application.yml` — `application.module-entitlements-cache.*`.
- `README.md`.

**New / modified tests:**
- `configuration/cache/ModuleEntitlementsCacheConfigurationTest.java` (`@UnitTest`).
- `service/ModuleEntitlementsCacheProviderTest.java` (`@UnitTest`, Spring slice).
- `service/ModuleEntitlementsCacheEvictionTest.java` (`@UnitTest`, Spring slice).
- `service/EntitlementModuleServiceTest.java` (modify existing).
- `it/ModuleEntitlementsCacheIT.java` (`@IntegrationTest`).

---

## Task 1: Cache properties + configuration (gating fix + NoOp swap)

**Files:**
- Create: `src/main/java/org/folio/entitlement/configuration/cache/ModuleEntitlementsCacheProperties.java`
- Create: `src/main/java/org/folio/entitlement/configuration/cache/ModuleEntitlementsCacheConfiguration.java`
- Modify: `src/main/java/org/folio/entitlement/configuration/cache/CacheConfiguration.java`
- Modify: `src/main/java/org/folio/entitlement/integration/keycloak/KeycloakCacheableService.java`
- Test: `src/test/java/org/folio/entitlement/configuration/cache/ModuleEntitlementsCacheConfigurationTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/folio/entitlement/configuration/cache/ModuleEntitlementsCacheConfigurationTest.java`:

```java
package org.folio.entitlement.configuration.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.annotation.UserConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.support.NoOpCacheManager;

@UnitTest
class ModuleEntitlementsCacheConfigurationTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
    .withConfiguration(UserConfigurations.of(ModuleEntitlementsCacheConfiguration.class));

  @Test
  void enabledByDefault_createsCaffeineCacheManager() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(CacheManager.class);
      assertThat(ctx.getBean("moduleEntitlementsCacheManager")).isInstanceOf(CaffeineCacheManager.class);
      assertThat(((CaffeineCacheManager) ctx.getBean("moduleEntitlementsCacheManager")).getCacheNames())
        .contains(ModuleEntitlementsCacheConfiguration.MODULE_ENTITLEMENTS_CACHE);
    });
  }

  @Test
  void disabled_createsNoOpCacheManager() {
    runner.withPropertyValues("application.module-entitlements-cache.enabled=false").run(ctx ->
      assertThat(ctx.getBean("moduleEntitlementsCacheManager")).isInstanceOf(NoOpCacheManager.class));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=<java21> mvn test -Dtest=ModuleEntitlementsCacheConfigurationTest -Dcheckstyle.skip`
Expected: COMPILE FAILURE — `ModuleEntitlementsCacheConfiguration` does not exist.

- [ ] **Step 3: Create the properties class**

Create `src/main/java/org/folio/entitlement/configuration/cache/ModuleEntitlementsCacheProperties.java`:

```java
package org.folio.entitlement.configuration.cache;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "application.module-entitlements-cache")
public class ModuleEntitlementsCacheProperties {

  /**
   * Maximum number of cached per-module entitlement lists (Caffeine maximumSize).
   */
  private long maxSize = 1000;

  /**
   * Backstop expiry (Caffeine expireAfterWrite). Not the freshness mechanism — invalidation is
   * in-process and immediate on every write to the entitlement table.
   */
  private Duration ttl = Duration.ofMinutes(30);
}
```

- [ ] **Step 4: Create the cache configuration**

Create `src/main/java/org/folio/entitlement/configuration/cache/ModuleEntitlementsCacheConfiguration.java`:

```java
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

/**
 * Always-on cache configuration. Carries {@link EnableCaching} so caching works regardless of
 * {@code application.keycloak.enabled} (the keycloak-gated {@link CacheConfiguration} no longer
 * enables caching). Provides a {@code moduleEntitlementsCacheManager} bean that is a Caffeine
 * manager when enabled and a {@link NoOpCacheManager} when disabled — the bean name always resolves
 * so {@code @Cacheable/@CacheEvict(cacheManager = "moduleEntitlementsCacheManager")} is valid in
 * every profile.
 */
@Log4j2
@Configuration
@EnableCaching
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
```

- [ ] **Step 5: Remove `@EnableCaching` from the keycloak-gated config**

In `src/main/java/org/folio/entitlement/configuration/cache/CacheConfiguration.java`, delete the `@EnableCaching` annotation and its import. The header becomes:

```java
@Log4j2
@Configuration
@ConditionalOnProperty("application.keycloak.enabled")
public class CacheConfiguration {
```

(Remove `import org.springframework.cache.annotation.EnableCaching;`.)

- [ ] **Step 6: Qualify the access-token `@Cacheable`**

In `src/main/java/org/folio/entitlement/integration/keycloak/KeycloakCacheableService.java`, change:

```java
  @Cacheable(cacheNames = ACCESS_TOKEN, key = "#userToken")
```

to:

```java
  @Cacheable(cacheNames = ACCESS_TOKEN, cacheManager = "accessTokenCacheManager", key = "#userToken")
```

- [ ] **Step 7: Run test to verify it passes**

Run: `JAVA_HOME=<java21> mvn test -Dtest=ModuleEntitlementsCacheConfigurationTest -Dcheckstyle.skip`
Expected: PASS (2 tests).

- [ ] **Step 8: Commit**

```bash
git add src/main/java/org/folio/entitlement/configuration/cache/ModuleEntitlementsCacheProperties.java \
        src/main/java/org/folio/entitlement/configuration/cache/ModuleEntitlementsCacheConfiguration.java \
        src/main/java/org/folio/entitlement/configuration/cache/CacheConfiguration.java \
        src/main/java/org/folio/entitlement/integration/keycloak/KeycloakCacheableService.java \
        src/test/java/org/folio/entitlement/configuration/cache/ModuleEntitlementsCacheConfigurationTest.java
git commit -m "feat: add always-on module-entitlements cache manager (keycloak-independent)"
```

---

## Task 2: Cached provider + repository method

**Files:**
- Create: `src/main/java/org/folio/entitlement/service/ModuleEntitlementsCacheProvider.java`
- Modify: `src/main/java/org/folio/entitlement/repository/EntitlementModuleRepository.java`
- Test: `src/test/java/org/folio/entitlement/service/ModuleEntitlementsCacheProviderTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/folio/entitlement/service/ModuleEntitlementsCacheProviderTest.java`:

```java
package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.folio.entitlement.configuration.cache.ModuleEntitlementsCacheConfiguration;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.mapper.EntitlementModuleMapper;
import org.folio.entitlement.repository.EntitlementModuleRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@UnitTest
@SpringJUnitConfig({ModuleEntitlementsCacheConfiguration.class, ModuleEntitlementsCacheProvider.class})
class ModuleEntitlementsCacheProviderTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";

  @MockitoBean private EntitlementModuleRepository repository;
  @MockitoBean private EntitlementModuleMapper mapper;
  @Autowired private ModuleEntitlementsCacheProvider provider;

  private static EntitlementModuleEntity entity() {
    var e = new EntitlementModuleEntity();
    e.setModuleId(MODULE_ID);
    e.setTenantId(UUID.fromString("00000000-0000-0000-0000-000000000001"));
    e.setApplicationId("app-1.0.0");
    return e;
  }

  private static Entitlement entitlement() {
    return new Entitlement("app-1.0.0", UUID.fromString("00000000-0000-0000-0000-000000000001"));
  }

  @Test
  void getByModuleId_returnsMappedUnmodifiableList() {
    when(repository.findAllByModuleId(eq(MODULE_ID), any(Sort.class))).thenReturn(List.of(entity()));
    when(mapper.map(any(EntitlementModuleEntity.class))).thenReturn(entitlement());

    var result = provider.getByModuleId(MODULE_ID);

    assertThat(result).containsExactly(entitlement());
    assertThatThrownBy(() -> result.add(entitlement())).isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void getByModuleId_secondCallIsServedFromCache() {
    when(repository.findAllByModuleId(eq(MODULE_ID), any(Sort.class))).thenReturn(List.of(entity()));
    when(mapper.map(any(EntitlementModuleEntity.class))).thenReturn(entitlement());

    provider.getByModuleId(MODULE_ID);
    provider.getByModuleId(MODULE_ID);

    verify(repository, times(1)).findAllByModuleId(eq(MODULE_ID), any(Sort.class));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=<java21> mvn test -Dtest=ModuleEntitlementsCacheProviderTest -Dcheckstyle.skip`
Expected: COMPILE FAILURE — `ModuleEntitlementsCacheProvider` and `findAllByModuleId(String, Sort)` do not exist.

- [ ] **Step 3: Add the repository method**

In `src/main/java/org/folio/entitlement/repository/EntitlementModuleRepository.java`, add `import org.springframework.data.domain.Sort;` and one method below the existing `findAllByModuleId(String, Pageable)`:

```java
  List<EntitlementModuleEntity> findAllByModuleId(String moduleId, Sort sort);
```

- [ ] **Step 4: Create the provider**

Create `src/main/java/org/folio/entitlement/service/ModuleEntitlementsCacheProvider.java`:

```java
package org.folio.entitlement.service;

import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.configuration.cache.ModuleEntitlementsCacheConfiguration.MODULE_ENTITLEMENTS_CACHE;
import static org.folio.entitlement.domain.entity.key.EntitlementModuleEntity.SORT_BY_TENANT;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.mapper.EntitlementModuleMapper;
import org.folio.entitlement.repository.EntitlementModuleRepository;
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
}
```

`sync = true` collapses concurrent cache-miss loads for the same key (many sidecars polling the
same module ID) into a single DB load, preventing a stampede on miss/eviction. It is compatible
with a single named cache + key + `cacheManager`; do not add `unless`/multiple caches alongside it.

- [ ] **Step 5: Run test to verify it passes**

Run: `JAVA_HOME=<java21> mvn test -Dtest=ModuleEntitlementsCacheProviderTest -Dcheckstyle.skip`
Expected: PASS (2 tests).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/org/folio/entitlement/service/ModuleEntitlementsCacheProvider.java \
        src/main/java/org/folio/entitlement/repository/EntitlementModuleRepository.java \
        src/test/java/org/folio/entitlement/service/ModuleEntitlementsCacheProviderTest.java
git commit -m "feat: add cached ModuleEntitlementsCacheProvider keyed by moduleId"
```

---

## Task 3: Service delegates to the cached provider and paginates in memory

**Files:**
- Modify: `src/main/java/org/folio/entitlement/service/EntitlementModuleService.java`
- Test: `src/test/java/org/folio/entitlement/service/EntitlementModuleServiceTest.java`

- [ ] **Step 1: Update the existing test (write the failing tests)**

In `src/test/java/org/folio/entitlement/service/EntitlementModuleServiceTest.java`, add imports:

```java
import java.util.List;
import org.folio.entitlement.domain.dto.Entitlement;
```

Add a provider mock alongside the existing mocks:

```java
  @Mock private EntitlementModuleRepository repository;
  @Mock private EntitlementModuleMapper mapper;
  @Mock private ModuleEntitlementsCacheProvider cacheProvider;

  @InjectMocks private EntitlementModuleService service;
```

Replace the existing `getModuleEntitlements_positive` test with these four:

```java
  private static List<Entitlement> threeEntitlements() {
    return List.of(
      new Entitlement("app-1.0.0", UUID.fromString("00000000-0000-0000-0000-000000000001")),
      new Entitlement("app-1.0.0", UUID.fromString("00000000-0000-0000-0000-000000000002")),
      new Entitlement("app-1.0.0", UUID.fromString("00000000-0000-0000-0000-000000000003")));
  }

  @Test
  void getModuleEntitlements_returnsFirstPage() {
    when(cacheProvider.getByModuleId(MODULE_ID)).thenReturn(threeEntitlements());

    var actual = service.getModuleEntitlements(MODULE_ID, 2, 0);

    assertThat(actual.getTotalRecords()).isEqualTo(3);
    assertThat(actual.getEntitlements()).hasSize(2);
  }

  @Test
  void getModuleEntitlements_returnsSecondPartialPage() {
    when(cacheProvider.getByModuleId(MODULE_ID)).thenReturn(threeEntitlements());

    var actual = service.getModuleEntitlements(MODULE_ID, 2, 2);

    assertThat(actual.getTotalRecords()).isEqualTo(3);
    assertThat(actual.getEntitlements()).hasSize(1);
  }

  @Test
  void getModuleEntitlements_offsetBeyondSize_returnsEmptyPageWithTotal() {
    when(cacheProvider.getByModuleId(MODULE_ID)).thenReturn(threeEntitlements());

    var actual = service.getModuleEntitlements(MODULE_ID, 10, 50);

    assertThat(actual.getTotalRecords()).isEqualTo(3);
    assertThat(actual.getEntitlements()).isEmpty();
  }

  @Test
  void getModuleEntitlements_emptyModule_returnsEmpty() {
    when(cacheProvider.getByModuleId(MODULE_ID)).thenReturn(List.of());

    var actual = service.getModuleEntitlements(MODULE_ID, 10, 0);

    assertThat(actual.getTotalRecords()).isZero();
    assertThat(actual.getEntitlements()).isEmpty();
  }
```

(Leave `findModuleEntitlement_positive`, `save_positive`, `saveAll_positive`, `deleteModuleEntitlement_positive`, `deleteAll_positive` unchanged.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=<java21> mvn test -Dtest=EntitlementModuleServiceTest -Dcheckstyle.skip`
Expected: COMPILE FAILURE — `ModuleEntitlementsCacheProvider` is not a dependency of `EntitlementModuleService` yet.

- [ ] **Step 3: Rewrite `getModuleEntitlements` in the service**

In `src/main/java/org/folio/entitlement/service/EntitlementModuleService.java`:

Remove these imports (no longer used):

```java
import static org.folio.entitlement.domain.entity.key.EntitlementModuleEntity.SORT_BY_TENANT;
import org.folio.common.domain.model.OffsetRequest;
```

Add:

```java
import java.util.ArrayList;
```

Add the provider field (class uses `@RequiredArgsConstructor`):

```java
  private final EntitlementModuleRepository repository;
  private final EntitlementModuleMapper mapper;
  private final ModuleEntitlementsCacheProvider cacheProvider;
```

Replace the `getModuleEntitlements` method body with:

```java
  @Transactional(readOnly = true)
  public Entitlements getModuleEntitlements(String moduleId, Integer limit, Integer offset) {
    var all = cacheProvider.getByModuleId(moduleId);
    var total = all.size();
    var from = Math.min(Math.max(offset, 0), total);
    var to = Math.min(from + limit, total);

    return new Entitlements()
      .totalRecords(total)
      .entitlements(new ArrayList<>(all.subList(from, to)));
  }
```

(`ModuleEntitlementsCacheProvider` is in the same package, no import needed.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=<java21> mvn test -Dtest=EntitlementModuleServiceTest -Dcheckstyle.skip`
Expected: PASS (all tests, including the 4 new pagination tests and the unchanged write tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/org/folio/entitlement/service/EntitlementModuleService.java \
        src/test/java/org/folio/entitlement/service/EntitlementModuleServiceTest.java
git commit -m "feat: serve getModuleEntitlements from cache and paginate in memory"
```

---

## Task 4: Per-moduleId in-process eviction on writes

Every write to `entitlement_module` goes through `EntitlementModuleService`. Each write evicts
exactly the module IDs it touches (not the whole cache) — critical for a large deployment where many
unique module IDs are cached and entitlement changes happen continuously: a change to module A must
not flush module B's hot entry. Eviction goes through `ModuleEntitlementsCacheProvider.evict(moduleId)`
(a different bean), so the cache proxy applies.

**Files:**
- Modify: `src/main/java/org/folio/entitlement/service/ModuleEntitlementsCacheProvider.java`
- Modify: `src/main/java/org/folio/entitlement/service/EntitlementModuleService.java`
- Test: `src/test/java/org/folio/entitlement/service/ModuleEntitlementsCacheEvictionTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/folio/entitlement/service/ModuleEntitlementsCacheEvictionTest.java`:

```java
package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.folio.entitlement.configuration.cache.ModuleEntitlementsCacheConfiguration;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.mapper.EntitlementModuleMapper;
import org.folio.entitlement.repository.EntitlementModuleRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@UnitTest
@SpringJUnitConfig({
  ModuleEntitlementsCacheConfiguration.class,
  ModuleEntitlementsCacheProvider.class,
  EntitlementModuleService.class
})
class ModuleEntitlementsCacheEvictionTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final String APPLICATION_ID = "app-1.0.0";

  @MockitoBean private EntitlementModuleRepository repository;
  @MockitoBean private EntitlementModuleMapper mapper;
  @Autowired private ModuleEntitlementsCacheProvider provider;
  @Autowired private EntitlementModuleService service;
  @Autowired private CacheManager cacheManager;

  @Test
  void writeAffectingModule_evictsOnlyThatModulesEntry() {
    var entity = new EntitlementModuleEntity();
    entity.setModuleId(MODULE_ID);
    entity.setTenantId(TENANT_ID);
    entity.setApplicationId(APPLICATION_ID);
    when(repository.findAllByModuleId(any(String.class), any(Sort.class))).thenReturn(List.of(entity));
    when(mapper.map(any(EntitlementModuleEntity.class))).thenReturn(new Entitlement(APPLICATION_ID, TENANT_ID));

    var otherModuleId = "mod-bar-2.0.0";
    provider.getByModuleId(MODULE_ID);
    provider.getByModuleId(otherModuleId);
    assertThat(cache().get(MODULE_ID)).as("module under test cached").isNotNull();
    assertThat(cache().get(otherModuleId)).as("other module cached").isNotNull();

    service.deleteModuleEntitlement(MODULE_ID, TENANT_ID, APPLICATION_ID);

    assertThat(cache().get(MODULE_ID)).as("affected module evicted").isNull();
    assertThat(cache().get(otherModuleId)).as("unrelated module retained").isNotNull();
  }

  private org.springframework.cache.Cache cache() {
    return cacheManager.getCache(ModuleEntitlementsCacheConfiguration.MODULE_ENTITLEMENTS_CACHE);
  }
}
```

Note: this assumes `deleteModuleEntitlement(String, UUID, String)` builds its key with
`EntitlementModuleKey.of(moduleId, tenantId, applicationId)` directly (verified in the current
source), so no `mapper.mapKey` stub is needed. The per-key assertion (`otherModuleId` survives) is
what proves eviction is per-module rather than all-entries.

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=<java21> mvn test -Dtest=ModuleEntitlementsCacheEvictionTest -Dcheckstyle.skip`
Expected: FAIL — the `affected module evicted` assertion fails (`cache().get(MODULE_ID)` is still non-null) because eviction is not wired yet.

- [ ] **Step 3: Add an `evict` method to the provider**

In `src/main/java/org/folio/entitlement/service/ModuleEntitlementsCacheProvider.java`, add the import:

```java
import org.springframework.cache.annotation.CacheEvict;
```

and the method (alongside `getByModuleId`):

```java
  @CacheEvict(cacheNames = MODULE_ENTITLEMENTS_CACHE, cacheManager = "moduleEntitlementsCacheManager",
    key = "#moduleId")
  public void evict(String moduleId) {
    // body intentionally empty: eviction performed by @CacheEvict
  }
```

- [ ] **Step 4: Evict the affected module IDs in each write method**

In `src/main/java/org/folio/entitlement/service/EntitlementModuleService.java`, call
`cacheProvider.evict(...)` after the repository write in each of the five write methods (the
`cacheProvider` field already exists from Task 3). The method bodies become:

```java
  public void save(ModuleRequest moduleRequest) {
    var entity = mapper.map(moduleRequest);
    repository.save(entity);
    cacheProvider.evict(moduleRequest.getModuleId());
  }

  public void saveAll(UUID tenantId, String applicationId, List<String> modules) {
    var entities = toEntities(tenantId, applicationId, modules);
    repository.saveAll(entities);
    modules.forEach(cacheProvider::evict);
  }

  public void deleteModuleEntitlement(ModuleRequest moduleRequest) {
    var key = mapper.mapKey(moduleRequest);
    repository.deleteById(key);
    cacheProvider.evict(moduleRequest.getModuleId());
  }

  public void deleteModuleEntitlement(String moduleId, UUID tenantId, String applicationId) {
    var key = EntitlementModuleKey.of(moduleId, tenantId, applicationId);
    repository.deleteById(key);
    cacheProvider.evict(moduleId);
  }

  public void deleteAll(UUID tenantId, String applicationId, List<String> modules) {
    var keys = toModuleKeys(tenantId, applicationId, modules);
    repository.deleteAllById(keys);
    modules.forEach(cacheProvider::evict);
  }
```

(Do not evict in the read methods `getModuleEntitlements` / `findAllModuleEntitlements`. If a
write-method body on disk differs — e.g. a different helper name — keep the existing body and only add
the trailing `cacheProvider.evict(...)` line(s).)

- [ ] **Step 5: Run test to verify it passes**

Run: `JAVA_HOME=<java21> mvn test -Dtest=ModuleEntitlementsCacheEvictionTest -Dcheckstyle.skip`
Expected: PASS (1 test) — the affected module is evicted, the unrelated module stays cached.

- [ ] **Step 6: Re-run the service unit test (regression)**

Run: `JAVA_HOME=<java21> mvn test -Dtest=EntitlementModuleServiceTest -Dcheckstyle.skip`
Expected: PASS (the write tests still pass — `cacheProvider` is a Mockito mock there, so `evict` is a no-op).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/folio/entitlement/service/ModuleEntitlementsCacheProvider.java \
        src/main/java/org/folio/entitlement/service/EntitlementModuleService.java \
        src/test/java/org/folio/entitlement/service/ModuleEntitlementsCacheEvictionTest.java
git commit -m "feat: evict affected module entries from cache on entitlement writes"
```

---

## Task 5: Add config + integration test (caching active with keycloak off, write evicts)

**Files:**
- Modify: `src/main/resources/application.yml`
- Create: `src/test/java/org/folio/entitlement/it/ModuleEntitlementsCacheIT.java`

- [ ] **Step 1: Add config to `application.yml`**

In `src/main/resources/application.yml`, under the top-level `application:` block (near `application.environment`), add:

```yaml
  module-entitlements-cache:
    enabled: ${MODULE_ENTITLEMENTS_CACHE_ENABLED:true}
    max-size: ${MODULE_ENTITLEMENTS_CACHE_MAX_SIZE:1000}
    ttl: ${MODULE_ENTITLEMENTS_CACHE_TTL:30m}
```

- [ ] **Step 2: Write the integration test**

This IT runs under the `it` profile (`application.keycloak.enabled=false`), proving the gating fix:
`@Cacheable` is active without keycloak. It seeds a row, populates the cache via the provider, then
calls a write method and asserts the entry is gone — fully synchronous.

Create `src/test/java/org/folio/entitlement/it/ModuleEntitlementsCacheIT.java`:

```java
package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.configuration.cache.ModuleEntitlementsCacheConfiguration.MODULE_ENTITLEMENTS_CACHE;

import java.util.UUID;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.domain.entity.key.EntitlementModuleKey;
import org.folio.entitlement.repository.EntitlementModuleRepository;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.ModuleEntitlementsCacheProvider;
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
  void getByModuleId_isCachedWithKeycloakOff_andEvictedOnWrite() {
    var entity = new EntitlementModuleEntity();
    entity.setModuleId(MODULE_ID);
    entity.setTenantId(TENANT_ID);
    entity.setApplicationId(APPLICATION_ID);
    repository.save(entity);

    // caching is active even with keycloak disabled (it profile) — the gating fix
    assertThat(cacheProvider.getByModuleId(MODULE_ID)).hasSize(1);
    assertThat(cache().get(MODULE_ID)).as("entry cached").isNotNull();

    // a write to the entitlement table evicts the cache in-process
    entitlementModuleService.deleteModuleEntitlement(MODULE_ID, TENANT_ID, APPLICATION_ID);
    assertThat(cache().get(MODULE_ID)).as("entry evicted after write").isNull();
  }

  private Cache cache() {
    return cacheManager.getCache(MODULE_ENTITLEMENTS_CACHE);
  }
}
```

- [ ] **Step 3: Run the integration test to verify it passes**

Run: `JAVA_HOME=<java21> mvn test-compile failsafe:integration-test -Dit.test='**/ModuleEntitlementsCacheIT.java' -Dcheckstyle.skip`
Expected: PASS (1 test). The entry is non-null after the read and null after the write.

If `cacheManager` autowiring is ambiguous (the keycloak `accessTokenCacheManager` is absent in the `it` profile, so it should be the only manager): qualify with `@Qualifier("moduleEntitlementsCacheManager")` on the autowired field.

- [ ] **Step 4: Commit**

```bash
git add src/main/resources/application.yml \
        src/test/java/org/folio/entitlement/it/ModuleEntitlementsCacheIT.java
git commit -m "test: integration test for module-entitlements caching and eviction"
```

---

## Task 6: Documentation

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Document the cache and its configuration**

Add to `README.md` (near the caching/environment docs):

```markdown
### Module entitlements cache

`GET /entitlements/modules/{moduleId}` is served from an in-memory Caffeine cache keyed by
`moduleId` (the full per-module entitlement list; pagination is applied in memory). Because
mgr-tenant-entitlements runs as a single instance, the cache is invalidated in-process: a write to
the entitlement table (entitle / upgrade / revoke) evicts only the affected module IDs, so unrelated
modules stay cached. `expireAfterWrite` is only a backstop.

| Env var | Default | Description |
|---|---|---|
| `MODULE_ENTITLEMENTS_CACHE_ENABLED` | `true` | Enable the cache. When `false`, a no-op cache is used (every read hits the DB). |
| `MODULE_ENTITLEMENTS_CACHE_MAX_SIZE` | `1000` | Max number of cached per-module lists (Caffeine `maximumSize`). Set ≥ the number of distinct module IDs read in the deployment, or hot entries get LRU-evicted. |
| `MODULE_ENTITLEMENTS_CACHE_TTL` | `30m` | Backstop expiry (Caffeine `expireAfterWrite`). |
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: document module-entitlements cache configuration"
```

---

## Task 7: Full build + regression

- [ ] **Step 1: Run the full unit suite**

Run: `JAVA_HOME=<java21> mvn test`
Expected: PASS. Verify the existing access-token caching path still works after the `@EnableCaching` move and the `cacheManager` qualifier.

- [ ] **Step 2: Run the new IT once more with checkstyle on**

Run: `JAVA_HOME=<java21> mvn test-compile failsafe:integration-test -Dit.test='**/ModuleEntitlementsCacheIT.java'`
Expected: PASS.

- [ ] **Step 3: Final commit (only if checkstyle/cleanup changes were needed)**

```bash
git add -A
git commit -m "chore: checkstyle/cleanup for module-entitlements cache"
```

---

## Self-Review

**Spec coverage:**
- Cache shape (cache-by-moduleId, paginate in memory, separate provider bean, unmodifiable list, `sync = true`) → Tasks 2 & 3. ✓
- `@EnableCaching` gating fix + dedicated manager + NoOp swap + `cacheManager` qualifiers → Task 1. ✓
- Per-moduleId in-process eviction on all writes + TTL backstop → Task 4 (provider `evict` + service calls); ttl in Task 1. ✓
- Effectiveness at scale (per-key eviction, `sync = true` anti-stampede, `max-size` sizing) → Tasks 2, 4, 6. ✓
- Config keys (`enabled`, `max-size`, `ttl`) → Tasks 1 & 5. ✓
- Error handling (NoOp when disabled, immutable cached value, read falls through) → Tasks 1, 2. ✓
- Testing (config, provider caching, per-key eviction slice, pagination unit, IT for gating + eviction, access-token regression) → Tasks 1–5, 7. ✓
- README → Task 6. ✓
- Out of scope (by-tenant read, consumer-repo changes, Kafka consumer) → not implemented, by design. ✓

**Type/name consistency:**
- `MODULE_ENTITLEMENTS_CACHE = "module-entitlements"` — config, provider `@Cacheable`/`@CacheEvict`, eviction test, IT. ✓
- Cache manager bean name `"moduleEntitlementsCacheManager"` — both `@Bean(name=...)`, provider `@Cacheable`/`@CacheEvict`, config test. ✓
- `ModuleEntitlementsCacheProvider.getByModuleId(String)` / `evict(String)` — Tasks 2 & 4; used by service (Tasks 3, 4) and tests (Tasks 2, 4, 5). ✓
- Repository `findAllByModuleId(String, Sort)` — Task 2; used by provider. ✓
- Property prefix `application.module-entitlements-cache` — properties class, conditionals, yaml. ✓

**Watch-outs flagged inline:**
- Write-method bodies on disk may differ slightly — add only the trailing `cacheProvider.evict(...)` line(s) (Task 4 Step 4 note).
- `cacheManager` autowiring qualifier in the IT if ambiguous — Task 5 Step 3 note.
