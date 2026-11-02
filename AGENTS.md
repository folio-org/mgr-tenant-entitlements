# mgr-tenant-entitlements

FOLIO module managing tenant entitlements — application install/upgrade/revoke and dependency management via a flow-based execution engine. Java, Spring Boot, PostgreSQL/Liquibase, Kafka, Keycloak, Kong or APISIX.

## Build & Test

```bash
mvn clean install              # full build
mvn clean install -DskipTests  # skip tests
mvn test                       # unit tests (@UnitTest)
mvn verify                     # unit + integration (@IntegrationTest, centered on BaseIntegrationTest); needs Docker
mvn test -Dtest=ModuleDescriptorTest#moduleDescriptor_positive_publishedEventsDeclared  # single test
mvn clean install -Pcoverage   # coverage → target/site/jacoco-aggregate/index.html
mvn checkstyle:check           # runs during build; max method length 23 lines
```

Docker: `docker build -t mgr-tenant-entitlements .` Full env vars in `README.md`.

## Architecture

- **Kafka** (`integration/kafka/`): publishes entitlement, system-user, capability and scheduled-job events
  (`KafkaEventUtils`); consumes `resource-result` acknowledgments of its own events. The latter three are
  lifecycle-generic (CREATE/UPDATE/DELETE on one topic via `AbstractModuleEventPublisher`, DELETE reachable only
  through the Upgrade flow), not creation-only, so their declared identifiers carry no CRUD suffix. The module
  descriptor declares these under `producesEvents`/`consumesEvents` (deliberately decoupled from the Kafka topic
  names; see `KafkaEventUtils.PRODUCED_EVENT_*`/`CONSUMED_EVENT_RESOURCE_RESULT`), guarded by
  `ModuleDescriptorTest` (MGRENTITLE-211). capabilitySet is not declared separately — see `README.md`'s Kafka
  Integration section.
- **API Gateway route lifecycle**: routes are created on first entitlement (`ApiGatewayModuleRouteCreator`) and deleted when the last tenant revokes (`ApiGatewayModuleRouteCleaner`), when an upgrade removes or replaces a module that no other tenant uses (`ApiGatewayModuleRouteUpdater`), and on rollback with `purgeOnRollback=true` (`ApiGatewayModuleRouteCreator.cancel`). Shared-route checks use `EntitlementModuleService.isEntitlementExist` / `isNoOtherEntitlementExist`.

## Conventions

- **Codegen**: spec `src/main/resources/swagger.api/mgr-tenant-entitlements.yaml` → `org.folio.entitlement.rest.resource` + `.domain.dto` in `target/generated-sources/` (do not edit).
- **DB**: Liquibase `changelog/changelog-master.xml`; note mixed dir naming (`changes/v1.0.0/`, `changes.v3.0.0/`).
- **Tests**: `@UnitTest` (Mockito), `@IntegrationTest` (extend `BaseIntegrationTest`, Testcontainers + WireMock). Gateway ITs use `@EnableApiGateway` extension (see `ApiGatewayRegistrationIT`, `ApiGatewayRouteManagementIT`). APISIX ITs additionally use `@EnableApisixGateway` (etcd + `folioci/folio-apisix:latest` containers; the image is amd64-only — on arm64 set `TESTCONTAINERS_APISIX_IMAGE` to a locally built image or rely on emulation). See `ApisixRouteManagementIT`, `ApisixRegistrationIT`.
- **Key env vars**: `MT_CLIENT_URL`, `AM_CLIENT_URL`, `APIGW_URL`; toggles `APIGW_ENABLED`/`KC_INTEGRATION_ENABLED`/`APIGW_ROUTEMANAGEMENT_ENABLED` (default `true`)/`APIGW_TENANT_CHECKS_ENABLED` (default `false`); `FLOW_ENGINE_*` (threads, timeout, cache); `VALIDATION_INTERFACE_*`; `ENV` (Kafka topic prefix). Full list in `README.md`.

## Pitfalls

- Don't edit generated DTOs/API interfaces in `target/generated-sources/`.
- Stages must be idempotent where possible (may be retried).
- Watch ThreadLocal usage in `ThreadLocalModuleStageContext`.
- Kafka topic names are prefixed with `ENV`.
- Token refresh is automatic, but keep long operations cancellable.
- `KONG_*` env vars and `application.kong.*` properties are deprecated aliases that still bind via fallback expressions in `application.yml` and log a startup `WARN`; existing configs keep working until the Vetch release, but new code should use `APIGW_*` / `application.apigw.*` names.

Resources: `README.md` (env vars), `NEWS.md` (release notes).
