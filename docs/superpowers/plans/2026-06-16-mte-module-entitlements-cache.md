# Module-Entitlements Server-Side Cache Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Cache `EntitlementModuleService.getModuleEntitlements(moduleId, …)` in mgr-tenant-entitlements so repeated reads from the sidecar don't hit the DB, kept fresh across replicas by a Kafka broadcast consumer with a TTL backstop.

**Architecture:** Cache the full per-module entitlement list keyed on `moduleId` in a dedicated Caffeine cache; paginate in memory. The cached read lives in its own bean (`ModuleEntitlementsCacheProvider`) so Spring's cache proxy isn't bypassed. A new per-instance broadcast Kafka consumer on `${ENV}.entitlement` evicts the affected `moduleId` on every replica; `expireAfterWrite` is a missed-event backstop. `@EnableCaching` moves to an always-on config so the cache works regardless of `application.keycloak.enabled`.

**Tech Stack:** Spring Boot 4.1.0, Java 21, Spring Cache + Caffeine 3.1.8, Spring Kafka, JUnit 5 + Mockito + AssertJ + Awaitility, Testcontainers (Postgres) + embedded Kafka.

**Design spec:** `docs/superpowers/specs/2026-06-16-mte-module-entitlements-cache-design.md`

## Conventions for this repo (read before running anything)

- **JDK:** `mvn` here defaults to Java 26 and silently breaks Lombok. Export `JAVA_HOME` to a Java 21 JDK before every Maven command.
- **Unit tests** are `@UnitTest` (surefire): run with `mvn test -Dtest=<ClassName>`.
- **Integration tests** are `@IntegrationTest` (failsafe). `failsafe:integration-test` does NOT run `test-compile`, so a new/edited IT runs stale or "not found" — always prepend `test-compile`. During TDD add `-Dcheckstyle.skip` to focus on the failure:
  `mvn test-compile failsafe:integration-test -Dit.test='**/<ClassName>.java' -Dcheckstyle.skip`

---

## File Structure

**New files (all under `src/main/java/org/folio/entitlement`):**
- `configuration/cache/ModuleEntitlementsCacheProperties.java` — `@ConfigurationProperties` (maxSize, ttl, groupIdPrefix).
- `configuration/cache/ModuleEntitlementsCacheConfiguration.java` — always-on `@EnableCaching`, the `moduleEntitlementsCacheManager` (Caffeine when enabled / NoOp when disabled), cache-name constant.
- `service/ModuleEntitlementsCacheProvider.java` — the `@Cacheable` read + `@CacheEvict` in a dedicated bean.
- `integration/kafka/configuration/ModuleEntitlementsCacheConsumerConfiguration.java` — broadcast consumer factory + listener container factory.
- `integration/kafka/ModuleEntitlementsCacheInvalidationListener.java` — `@KafkaListener` that evicts by `moduleId`.

**Modified files:**
- `configuration/cache/CacheConfiguration.java` — remove `@EnableCaching` (moved to the always-on config).
- `integration/keycloak/KeycloakCacheableService.java` — add `cacheManager = "accessTokenCacheManager"` to its `@Cacheable` (two managers now exist → must qualify).
- `service/EntitlementModuleService.java` — `getModuleEntitlements` delegates to the provider and paginates in memory.
- `repository/EntitlementModuleRepository.java` — add `findAllByModuleId(String, Sort)`.
- `src/main/resources/application.yml` — add `spring.kafka.topics.entitlement` + `application.module-entitlements-cache.*`.
- `README.md` — document the cache and its env vars.

**New tests:**
- `src/test/java/org/folio/entitlement/configuration/cache/ModuleEntitlementsCacheConfigurationTest.java` (`@UnitTest`).
- `src/test/java/org/folio/entitlement/service/ModuleEntitlementsCacheProviderTest.java` (`@UnitTest`, Spring slice).
- `src/test/java/org/folio/entitlement/integration/kafka/ModuleEntitlementsCacheInvalidationListenerTest.java` (`@UnitTest`).
- `src/test/java/org/folio/entitlement/it/ModuleEntitlementsCacheIT.java` (`@IntegrationTest`).
- `src/test/java/org/folio/entitlement/service/EntitlementModuleServiceTest.java` (modify existing).

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
   * Missed-event / memory backstop expiry (Caffeine expireAfterWrite). Not the freshness mechanism —
   * invalidation is event-driven via the entitlement Kafka topic.
   */
  private Duration ttl = Duration.ofMinutes(30);

  /**
   * Base name of the per-instance Kafka consumer group used for broadcast cache invalidation. A
   * random UUID is appended so every replica forms its own group and receives every event. Override
   * via {@code KAFKA_MODULE_ENTITLEMENTS_CACHE_GROUP_ID}; the default is environment-prefixed in config.
   */
  private String groupIdPrefix = "mgr-tenant-entitlements-module-entitlements-cache";
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
 * so {@code @Cacheable(cacheManager = "moduleEntitlementsCacheManager")} is valid in every profile.
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

In `src/main/java/org/folio/entitlement/configuration/cache/CacheConfiguration.java`, delete the `@EnableCaching` line and its import. The class header changes from:

```java
import org.springframework.cache.annotation.EnableCaching;
...
@Log4j2
@Configuration
@EnableCaching
@ConditionalOnProperty("application.keycloak.enabled")
public class CacheConfiguration {
```

to:

```java
@Log4j2
@Configuration
@ConditionalOnProperty("application.keycloak.enabled")
public class CacheConfiguration {
```

(Remove the now-unused `import org.springframework.cache.annotation.EnableCaching;`.)

- [ ] **Step 6: Qualify the access-token `@Cacheable`**

In `src/main/java/org/folio/entitlement/integration/keycloak/KeycloakCacheableService.java`, change:

```java
  @Cacheable(cacheNames = ACCESS_TOKEN, key = "#userToken")
  public AccessTokenResponse getAccessToken(String userToken) {
```

to:

```java
  @Cacheable(cacheNames = ACCESS_TOKEN, cacheManager = "accessTokenCacheManager", key = "#userToken")
  public AccessTokenResponse getAccessToken(String userToken) {
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
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@UnitTest
@SpringJUnitConfig({ModuleEntitlementsCacheConfiguration.class, ModuleEntitlementsCacheProvider.class})
class ModuleEntitlementsCacheProviderTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";

  @MockitoBean private EntitlementModuleRepository repository;
  @MockitoBean private EntitlementModuleMapper mapper;
  @org.springframework.beans.factory.annotation.Autowired private ModuleEntitlementsCacheProvider provider;

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

  @Test
  void evict_forcesRepositoryReloadOnNextCall() {
    when(repository.findAllByModuleId(eq(MODULE_ID), any(Sort.class))).thenReturn(List.of(entity()));
    when(mapper.map(any(EntitlementModuleEntity.class))).thenReturn(entitlement());

    provider.getByModuleId(MODULE_ID);
    provider.evict(MODULE_ID);
    provider.getByModuleId(MODULE_ID);

    verify(repository, times(2)).findAllByModuleId(eq(MODULE_ID), any(Sort.class));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=<java21> mvn test -Dtest=ModuleEntitlementsCacheProviderTest -Dcheckstyle.skip`
Expected: COMPILE FAILURE — `ModuleEntitlementsCacheProvider` and `findAllByModuleId(String, Sort)` do not exist.

- [ ] **Step 3: Add the repository method**

In `src/main/java/org/folio/entitlement/repository/EntitlementModuleRepository.java`, add an import and one method. After the existing `findAllByModuleId(String, Pageable)` line, the body becomes:

```java
import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.domain.entity.key.EntitlementModuleKey;
import org.folio.spring.cql.JpaCqlRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EntitlementModuleRepository extends JpaCqlRepository<EntitlementModuleEntity, EntitlementModuleKey> {

  Page<EntitlementModuleEntity> findAllByModuleId(String moduleId, Pageable pageable);

  List<EntitlementModuleEntity> findAllByModuleId(String moduleId, Sort sort);

  List<EntitlementModuleEntity> findAllByModuleIdAndTenantId(String moduleId, UUID tenantId);

  @Query("""
    select entity from EntitlementModuleEntity entity
      where entity.applicationId = :applicationId
        and entity.tenantId = :tenantId
    order by entity.moduleId""")
  List<EntitlementModuleEntity> findAllByApplicationIdAndTenantId(
    @Param("applicationId") String applicationId, @Param("tenantId") UUID tenantId);
}
```

(Only the `import org.springframework.data.domain.Sort;` and the `findAllByModuleId(String moduleId, Sort sort)` line are new.)

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
    key = "#moduleId")
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
```

- [ ] **Step 5: Run test to verify it passes**

Run: `JAVA_HOME=<java21> mvn test -Dtest=ModuleEntitlementsCacheProviderTest -Dcheckstyle.skip`
Expected: PASS (3 tests).

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

In `src/test/java/org/folio/entitlement/service/EntitlementModuleServiceTest.java`, add the provider mock and replace the `getModuleEntitlements_positive` test with pagination tests. Add these imports near the top:

```java
import java.util.List;
import org.folio.entitlement.domain.dto.Entitlement;
```

Add a mock field alongside the existing mocks:

```java
  @Mock private EntitlementModuleRepository repository;
  @Mock private EntitlementModuleMapper mapper;
  @Mock private ModuleEntitlementsCacheProvider cacheProvider;

  @InjectMocks private EntitlementModuleService service;
```

Replace the existing `getModuleEntitlements_positive` test with:

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

(Leave the other existing tests — `findModuleEntitlement_positive`, `save_positive`, `saveAll_positive`, `deleteModuleEntitlement_positive`, `deleteAll_positive` — unchanged.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=<java21> mvn test -Dtest=EntitlementModuleServiceTest -Dcheckstyle.skip`
Expected: COMPILE FAILURE — `ModuleEntitlementsCacheProvider` is not a constructor dependency of `EntitlementModuleService` yet.

- [ ] **Step 3: Rewrite `getModuleEntitlements` in the service**

In `src/main/java/org/folio/entitlement/service/EntitlementModuleService.java`:

Remove these two imports (no longer used):

```java
import static org.folio.entitlement.domain.entity.key.EntitlementModuleEntity.SORT_BY_TENANT;
import org.folio.common.domain.model.OffsetRequest;
```

Add this import:

```java
import java.util.ArrayList;
```

Add the provider field (the class already uses `@RequiredArgsConstructor`):

```java
  private final EntitlementModuleRepository repository;
  private final EntitlementModuleMapper mapper;
  private final ModuleEntitlementsCacheProvider cacheProvider;
```

Replace the `getModuleEntitlements` method with:

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

(`ModuleEntitlementsCacheProvider` is in the same package `org.folio.entitlement.service`, so no import is needed.)

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

## Task 4: Kafka broadcast consumer + invalidation listener

**Files:**
- Create: `src/main/java/org/folio/entitlement/integration/kafka/configuration/ModuleEntitlementsCacheConsumerConfiguration.java`
- Create: `src/main/java/org/folio/entitlement/integration/kafka/ModuleEntitlementsCacheInvalidationListener.java`
- Modify: `src/main/resources/application.yml`
- Test: `src/test/java/org/folio/entitlement/integration/kafka/ModuleEntitlementsCacheInvalidationListenerTest.java`

- [ ] **Step 1: Write the failing test**

Create `src/test/java/org/folio/entitlement/integration/kafka/ModuleEntitlementsCacheInvalidationListenerTest.java`:

```java
package org.folio.entitlement.integration.kafka;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.UUID;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.service.ModuleEntitlementsCacheProvider;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleEntitlementsCacheInvalidationListenerTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";

  @Mock private ModuleEntitlementsCacheProvider cacheProvider;
  @InjectMocks private ModuleEntitlementsCacheInvalidationListener listener;

  @Test
  void onEntitlementEvent_evictsByModuleId() {
    listener.onEntitlementEvent(new EntitlementEvent("REVOKE", MODULE_ID, "tenant", UUID.randomUUID()));
    verify(cacheProvider).evict(MODULE_ID);
  }

  @Test
  void onEntitlementEvent_blankModuleId_doesNotEvict() {
    listener.onEntitlementEvent(new EntitlementEvent("REVOKE", "  ", "tenant", UUID.randomUUID()));
    verify(cacheProvider, never()).evict(org.mockito.ArgumentMatchers.any());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `JAVA_HOME=<java21> mvn test -Dtest=ModuleEntitlementsCacheInvalidationListenerTest -Dcheckstyle.skip`
Expected: COMPILE FAILURE — `ModuleEntitlementsCacheInvalidationListener` does not exist.

- [ ] **Step 3: Create the consumer configuration**

Create `src/main/java/org/folio/entitlement/integration/kafka/configuration/ModuleEntitlementsCacheConsumerConfiguration.java`:

```java
package org.folio.entitlement.integration.kafka.configuration;

import static org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.entitlement.configuration.cache.ModuleEntitlementsCacheProperties;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Broadcast consumer for module-entitlements cache invalidation. A unique group per instance
 * (UUID-suffixed prefix) ensures every replica receives every entitlement event. Offsets are never
 * committed (ack mode MANUAL, no auto-commit) and reset to latest, so these throwaway groups leave
 * no lingering metadata and resume from start-up onward. Mirrors mgr-applications' bootstrap-cache
 * consumer.
 */
@Log4j2
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.module-entitlements-cache.enabled", havingValue = "true",
  matchIfMissing = true)
public class ModuleEntitlementsCacheConsumerConfiguration {

  private final KafkaProperties kafkaProperties;
  private final ModuleEntitlementsCacheProperties cacheProperties;

  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, EntitlementEvent>
    moduleEntitlementsCacheKafkaListenerContainerFactory(
      ConsumerFactory<String, EntitlementEvent> moduleEntitlementsCacheConsumerFactory) {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, EntitlementEvent>();
    factory.setConsumerFactory(moduleEntitlementsCacheConsumerFactory);
    factory.setCommonErrorHandler(errorHandler());
    factory.getContainerProperties().setAckMode(AckMode.MANUAL);
    return factory;
  }

  @Bean
  public ConsumerFactory<String, EntitlementEvent> moduleEntitlementsCacheConsumerFactory() {
    var deserializer = new JacksonJsonDeserializer<>(EntitlementEvent.class);
    Map<String, Object> config = new HashMap<>(kafkaProperties.buildConsumerProperties());
    config.put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    config.put(VALUE_DESERIALIZER_CLASS_CONFIG, deserializer);
    config.put(GROUP_ID_CONFIG, cacheProperties.getGroupIdPrefix() + "-" + UUID.randomUUID());
    config.put(AUTO_OFFSET_RESET_CONFIG, "latest");
    config.put(ENABLE_AUTO_COMMIT_CONFIG, false);
    return new DefaultKafkaConsumerFactory<>(config, new StringDeserializer(), deserializer);
  }

  private DefaultErrorHandler errorHandler() {
    var handler = new DefaultErrorHandler((record, ex) ->
      log.warn("Failed to process entitlement event for cache invalidation [record: {}]",
        record, ex.getCause()));
    // best-effort: eviction is idempotent, so no retry/backoff
    handler.setBackOffFunction((record, ex) -> new FixedBackOff(0L, 0L));
    handler.setLogLevel(KafkaException.Level.INFO);
    return handler;
  }
}
```

Note: if `kafkaProperties.buildConsumerProperties()` fails to compile under Spring Boot 4.1, use `kafkaProperties.buildConsumerProperties(null)` (the `SslBundles`-aware overload). Verify against `mgr-applications` which compiles with the no-arg form.

- [ ] **Step 4: Create the listener**

Create `src/main/java/org/folio/entitlement/integration/kafka/ModuleEntitlementsCacheInvalidationListener.java`:

```java
package org.folio.entitlement.integration.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.service.ModuleEntitlementsCacheProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes entitlement events from {@code {ENV}.entitlement} (per-instance broadcast group) and
 * evicts the affected module's cached entitlements on this replica. ENTITLE / UPGRADE / REVOKE all
 * evict identically; eviction is idempotent, so duplicate delivery is harmless.
 */
@Log4j2
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.module-entitlements-cache.enabled", havingValue = "true",
  matchIfMissing = true)
public class ModuleEntitlementsCacheInvalidationListener {

  private final ModuleEntitlementsCacheProvider cacheProvider;

  @KafkaListener(
    id = "module-entitlements-cache-invalidation-listener",
    containerFactory = "moduleEntitlementsCacheKafkaListenerContainerFactory",
    topics = "${spring.kafka.topics.entitlement}")
  public void onEntitlementEvent(EntitlementEvent event) {
    var moduleId = event == null ? null : event.getModuleId();
    if (StringUtils.isBlank(moduleId)) {
      log.warn("Skipping entitlement event with no moduleId: {}", event);
      return;
    }
    log.debug("Invalidating module-entitlements cache on entitlement event: moduleId={}", moduleId);
    cacheProvider.evict(moduleId);
  }
}
```

- [ ] **Step 5: Add config to `application.yml`**

In `src/main/resources/application.yml`, under the existing `spring.kafka:` block (which currently has `bootstrap-servers`, `security`, `ssl`, `producer`), add a `topics` key:

```yaml
  kafka:
    bootstrap-servers: ${KAFKA_HOST:kafka}:${KAFKA_PORT:9092}
    # ... existing security/ssl/producer ...
    topics:
      entitlement: ${KAFKA_ENTITLEMENT_TOPIC:${application.environment}.entitlement}
```

And under the top-level `application:` block, add the cache block (place it near the existing `application.environment` / `application.kafka` keys):

```yaml
application:
  # ... existing keys ...
  environment: ${ENV:folio}
  module-entitlements-cache:
    enabled: ${MODULE_ENTITLEMENTS_CACHE_ENABLED:true}
    max-size: ${MODULE_ENTITLEMENTS_CACHE_MAX_SIZE:1000}
    ttl: ${MODULE_ENTITLEMENTS_CACHE_TTL:30m}
    group-id-prefix: ${KAFKA_MODULE_ENTITLEMENTS_CACHE_GROUP_ID:${application.environment}-mgr-tenant-entitlements-module-entitlements-cache}
```

- [ ] **Step 6: Run test to verify it passes**

Run: `JAVA_HOME=<java21> mvn test -Dtest=ModuleEntitlementsCacheInvalidationListenerTest -Dcheckstyle.skip`
Expected: PASS (2 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/org/folio/entitlement/integration/kafka/configuration/ModuleEntitlementsCacheConsumerConfiguration.java \
        src/main/java/org/folio/entitlement/integration/kafka/ModuleEntitlementsCacheInvalidationListener.java \
        src/main/resources/application.yml \
        src/test/java/org/folio/entitlement/integration/kafka/ModuleEntitlementsCacheInvalidationListenerTest.java
git commit -m "feat: invalidate module-entitlements cache via broadcast Kafka consumer"
```

---

## Task 5: Integration test — caching active (keycloak off) + event-driven eviction

**Files:**
- Create: `src/test/java/org/folio/entitlement/it/ModuleEntitlementsCacheIT.java`
- (Possibly modify) `pom.xml` — only if `org.awaitility:awaitility` is not already a test dependency.

This IT runs under the `it` profile (`application.keycloak.enabled=false`), so it also proves the gating fix: `@Cacheable` is active without keycloak. It seeds a row directly, populates the cache through the provider, publishes a real `EntitlementEvent` to the embedded Kafka, and asserts the entry is evicted.

- [ ] **Step 1: Confirm Awaitility is available**

Run: `grep -n "awaitility" pom.xml`
Expected: a test-scoped `org.awaitility:awaitility` dependency. If absent, add it under `<dependencies>`:

```xml
<dependency>
  <groupId>org.awaitility</groupId>
  <artifactId>awaitility</artifactId>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Write the integration test**

Create `src/test/java/org/folio/entitlement/it/ModuleEntitlementsCacheIT.java`:

```java
package org.folio.entitlement.it;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.folio.entitlement.configuration.cache.ModuleEntitlementsCacheConfiguration.MODULE_ENTITLEMENTS_CACHE;

import java.util.UUID;
import org.folio.entitlement.domain.entity.key.EntitlementModuleEntity;
import org.folio.entitlement.domain.entity.key.EntitlementModuleKey;
import org.folio.entitlement.integration.kafka.EntitlementEventPublisher;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.repository.EntitlementModuleRepository;
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
  @Autowired private EntitlementEventPublisher eventPublisher;
  @Autowired private ModuleEntitlementsCacheProvider cacheProvider;

  @AfterEach
  void cleanUp() {
    repository.deleteById(EntitlementModuleKey.of(MODULE_ID, TENANT_ID, APPLICATION_ID));
    cache().clear();
  }

  @Test
  void getByModuleId_isCachedThenEvictedByEntitlementEvent() {
    var entity = new EntitlementModuleEntity();
    entity.setModuleId(MODULE_ID);
    entity.setTenantId(TENANT_ID);
    entity.setApplicationId(APPLICATION_ID);
    repository.save(entity);

    // cache is active even with keycloak disabled (the it profile) — populate it
    assertThat(cacheProvider.getByModuleId(MODULE_ID)).hasSize(1);
    assertThat(cache().get(MODULE_ID)).as("entry cached").isNotNull();

    // publishing an entitlement event for this module evicts the entry on this replica.
    // re-publish on each poll to absorb the broadcast consumer's start-up assignment lag
    // (it uses auto-offset-reset=latest, so an event sent before assignment would be missed).
    await().atMost(30, SECONDS).pollInterval(1, SECONDS).untilAsserted(() -> {
      eventPublisher.publish(new EntitlementEvent("REVOKE", MODULE_ID, "it-tenant", TENANT_ID));
      assertThat(cache().get(MODULE_ID)).as("entry evicted after event").isNull();
    });
  }

  private Cache cache() {
    return cacheManager.getCache(MODULE_ENTITLEMENTS_CACHE);
  }
}
```

- [ ] **Step 3: Run the integration test to verify it passes**

Run: `JAVA_HOME=<java21> mvn test-compile failsafe:integration-test -Dit.test='**/ModuleEntitlementsCacheIT.java' -Dcheckstyle.skip`
Expected: PASS (1 test). The cache entry appears after `getByModuleId`, then becomes `null` once the listener consumes the published event.

If it hangs/fails on eviction: confirm the listener subscribed to the same topic the publisher writes to — `EntitlementEventPublisher` uses `KafkaUtils.getEnvTopicName("entitlement")` and the listener uses `${spring.kafka.topics.entitlement}` = `${application.environment}.entitlement`. In the `it` profile `application.environment=it`, so both must resolve to `it.entitlement`. If they differ, align `spring.kafka.topics.entitlement` to whatever `getEnvTopicName("entitlement")` produces.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/org/folio/entitlement/it/ModuleEntitlementsCacheIT.java pom.xml
git commit -m "test: integration test for module-entitlements cache + event-driven eviction"
```

---

## Task 6: Documentation

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Document the cache and its configuration**

Add a section to `README.md` (under the existing caching/environment documentation, or near the Kafka section) describing:

```markdown
### Module entitlements cache

`GET /entitlements/modules/{moduleId}` is served from an in-memory Caffeine cache keyed by
`moduleId` (the full per-module entitlement list; pagination is applied in memory). The cache is
invalidated event-driven: a per-instance broadcast Kafka consumer on `${ENV}.entitlement` evicts
the affected `moduleId` on every replica when an entitlement is created, upgraded, or revoked.
`expireAfterWrite` is a missed-event backstop, not the primary freshness mechanism.

| Env var | Default | Description |
|---|---|---|
| `MODULE_ENTITLEMENTS_CACHE_ENABLED` | `true` | Enable the cache. When `false`, a no-op cache is used and the consumer is not started. |
| `MODULE_ENTITLEMENTS_CACHE_MAX_SIZE` | `1000` | Max number of cached per-module lists (Caffeine `maximumSize`). |
| `MODULE_ENTITLEMENTS_CACHE_TTL` | `30m` | Backstop expiry (Caffeine `expireAfterWrite`). |
| `KAFKA_MODULE_ENTITLEMENTS_CACHE_GROUP_ID` | `${ENV}-mgr-tenant-entitlements-module-entitlements-cache` | Base consumer-group name; a UUID is appended per instance for broadcast delivery. |
| `KAFKA_ENTITLEMENT_TOPIC` | `${ENV}.entitlement` | Topic the invalidation consumer subscribes to. |
```

- [ ] **Step 2: Commit**

```bash
git add README.md
git commit -m "docs: document module-entitlements cache configuration"
```

---

## Task 7: Full build + regression

- [ ] **Step 1: Run the full unit test suite**

Run: `JAVA_HOME=<java21> mvn test`
Expected: PASS. Pay attention that the existing `KeycloakCacheableService` / access-token caching tests (if any) still pass after the `@EnableCaching` move and the `cacheManager` qualifier.

- [ ] **Step 2: Run the new integration test once more in a clean compile**

Run: `JAVA_HOME=<java21> mvn test-compile failsafe:integration-test -Dit.test='**/ModuleEntitlementsCacheIT.java'`
Expected: PASS (with checkstyle on).

- [ ] **Step 3: Final commit (if any checkstyle fixes were needed)**

```bash
git add -A
git commit -m "chore: checkstyle/cleanup for module-entitlements cache"
```

---

## Self-Review

**Spec coverage:**
- Cache shape (cache-by-moduleId, paginate in memory, separate provider bean, unmodifiable list) → Tasks 2 & 3. ✓
- `@EnableCaching` gating fix + dedicated manager + NoOp swap + `cacheManager` qualifiers → Task 1. ✓
- Kafka broadcast consumer (per-instance UUID group, latest offset, ephemeral, evict by moduleId) + TTL backstop → Tasks 1 (ttl) & 4. ✓
- Config keys (`enabled`, `max-size`, `ttl`, group id, topic) → Tasks 1 & 4. ✓
- Error handling (best-effort listener, NoOp when disabled, immutable cached value) → Tasks 1, 2, 4. ✓
- Testing (pagination unit, provider caching slice, listener unit, IT for gating + eviction, access-token regression) → Tasks 2, 3, 4, 5, 7. ✓
- README → Task 6. ✓
- Out of scope (by-tenant read, consumer-repo changes) → not implemented, by design. ✓

**Type/name consistency check:**
- Cache name constant `MODULE_ENTITLEMENTS_CACHE = "module-entitlements"` used by config, provider, IT. ✓
- Cache manager bean name `"moduleEntitlementsCacheManager"` used by both `@Bean(name=...)` methods, the provider's `@Cacheable`/`@CacheEvict`, and the config test. ✓
- Listener `id = "module-entitlements-cache-invalidation-listener"`, containerFactory `"moduleEntitlementsCacheKafkaListenerContainerFactory"` — defined in Task 4 config, referenced in Task 4 listener. ✓
- Provider methods `getByModuleId(String)` / `evict(String)` — defined in Task 2, used in Task 3 (service), Task 4 (listener), Task 5 (IT). ✓
- Repository `findAllByModuleId(String, Sort)` — added in Task 2, used by provider. ✓
- Property prefix `application.module-entitlements-cache` consistent across properties class, conditionals, and yaml. ✓

**Open verification items (carried from the spec; the IT in Task 5 is the safety net):**
- `getEnvTopicName("entitlement")` must equal `${application.environment}.entitlement` — confirmed identical in the `it` profile or Task 5 fails (Step 3 note explains the fix).
- `kafkaProperties.buildConsumerProperties()` no-arg form must compile under Spring Boot 4.1 (mirrors mgr-applications; fallback noted in Task 4 Step 3).
- `org.awaitility:awaitility` test dependency present (Task 5 Step 1 adds it if missing).
