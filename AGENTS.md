# mgr-tenant-entitlements

FOLIO module managing tenant entitlements — application install/upgrade/revoke and dependency management via a flow-based execution engine. Spring Boot 4.0.6, Java 21, PostgreSQL/Liquibase, Kafka, Keycloak.

## Build & Test

```bash
mvn clean install              # full build
mvn clean install -DskipTests  # skip tests
mvn test                       # unit tests (@UnitTest)
mvn verify                     # unit + integration (@IntegrationTest, centered on BaseIntegrationTest)
mvn test -Dtest=EntitlementServiceTest#shouldReturnEntitlements  # single test
mvn clean install -Pcoverage   # coverage → target/site/jacoco-aggregate/index.html
mvn checkstyle:check           # runs during build; max method length 23 lines
```

Docker: `docker build -t mgr-tenant-entitlements .` Full env vars in `README.md`.

## Architecture

Built around the **Flow Engine** (`folio-flow-engine`), orchestrating multi-stage operations.

- **Flows** (`service/flow/`): Entitle (install), Upgrade, Revoke, Desired-State (reconcile).
- **Stages** (`service/stage/`): sequential discrete units (e.g. `ApplicationDescriptorLoader`, `TenantLoader`), retriable, parallelizable (`FLOW_ENGINE_MODULE_INSTALLER_THREADS`). Finalizers run on success and failure.
  ```
  FlowInitializer → Stage1 → … → StageN → FlowFinalizer
                                      ↓ (on failure) FailedFlowFinalizer
  ```
- **Dependencies**: builds `ApplicationInstallationGraph` / `ModuleInstallationGraph`; validates interface integrity; handles optional vs required and cross-application deps.
- **Integrations** (`integration/`): `am/`, `tm/`, `okapi/`, `folio/`, `keycloak/`, `kafka/`, `token/`, `interceptor/`.

**Domain concepts**: Entitlement (tenant's right to an application + modules), Application, Module, Flow Execution (DB-tracked), Stage Context (thread-local, see `ThreadLocalModuleStageContext`).

## Conventions

- **Packages**: `controller/` (implement OpenAPI interfaces) · `service/{flow,stage,validator}` · `integration/` · `repository/` · `domain/{entity,dto,model}` · `mapper/`.
- **Codegen**: spec `src/main/resources/swagger.api/mgr-tenant-entitlements.yaml` → `org.folio.entitlement.rest.resource` + `.domain.dto` in `target/generated-sources/` (do not edit).
- **MapStruct**: `@Mapper(componentModel="spring", injectionStrategy=CONSTRUCTOR)` in `mapper/`.
- **DI**: constructor injection (`@RequiredArgsConstructor`); bootstrap uses `@EnableKafkaProducer`, `@EnableMgrSecurity`, `@ConfigurationPropertiesScan`.
- **DB**: Liquibase `changelog/changelog-master.xml`; note mixed dir naming (`changes/v1.0.0/`, `changes.v3.0.0/`).
- **Security**: Keycloak via `@EnableMgrSecurity`; Caffeine token cache with auto-refresh for long ops.
- **Retry**: configurable for Keycloak/FOLIO calls (`retry/` package) + stage-level retry.
- **Tests**: `@UnitTest` (Mockito), `@IntegrationTest` (extend `BaseIntegrationTest`, Testcontainers + WireMock).
- **Key env vars**: `OKAPI_URL`, `MT_CLIENT_URL`, `AM_CLIENT_URL`, `KONG_ADMIN_URL`; toggles `KONG_/OKAPI_/KC_INTEGRATION_ENABLED`; `FLOW_ENGINE_*` (threads, timeout, cache); `VALIDATION_INTERFACE_*`; `ENV` (Kafka topic prefix). Full list in `README.md`.

## Pitfalls

- Don't edit generated DTOs/API interfaces in `target/generated-sources/`.
- Stages must be idempotent where possible (may be retried).
- Watch ThreadLocal usage in `ThreadLocalModuleStageContext`.
- Kafka topic names are prefixed with `ENV`.
- Token refresh is automatic, but keep long operations cancellable.

Resources: `README.md` (env vars), `NEWS.md` (release notes).
