# mgr-tenant-entitlements

FOLIO module managing tenant entitlements: application install/upgrade/revoke and dependency management via a flow-based execution engine (`folio-flow-engine`). Spring Boot, Java 21, PostgreSQL/Liquibase, Kafka, Keycloak.

## Build & Test

```bash
mvn clean install              # full build
mvn clean install -DskipTests  # skip tests
mvn test                       # unit tests (@UnitTest)
mvn verify                     # unit + integration (@IntegrationTest, centered on BaseIntegrationTest)
mvn test -Dtest=ModuleDescriptorTest#moduleDescriptor_positive_publishedEventsDeclared   # single unit test
mvn verify -Dit.test=ActuatorIT -Dtest=SkipSurefire -Dsurefire.failIfNoSpecifiedTests=false   # single IT, skips unit tests
mvn clean install -Pcoverage   # coverage → target/site/jacoco-aggregate/index.html
mvn checkstyle:check           # runs during build; max method length 23 lines (test sources exempt)
```

Docker: `docker build -t mgr-tenant-entitlements .` Full env vars in `README.md`.

## Style

Match the repository's existing style: use maintained implementations and tests as templates for new code, and keep the structure, naming, and conventions of neighboring files.

## Architecture notes

- Flows (`service/flow/`): Entitle (install), Upgrade, Revoke, Desired-State (reconcile). Stages live in `service/stage/` and, integration-specific, under `integration/`; they are retriable and parallelizable (`FLOW_ENGINE_MODULE_INSTALLER_THREADS`), with finalizers on success and failure (`FinishedFlowFinalizer`).
- Kafka (`integration/kafka/`): publishes entitlement, system-user, capability and scheduled-job events (`KafkaEventUtils`) and consumes `resource-result` acknowledgments of its own events. System-user, capability and scheduled-job events are lifecycle-generic (CREATE/UPDATE/DELETE each, DELETE reachable only through the Upgrade flow), so their declared identifiers carry no CRUD suffix. The module descriptor declares them under `producesEvents`/`consumesEvents`, deliberately decoupled from Kafka topic names; `ModuleDescriptorTest` guards this. `capabilitySet` is not declared separately; see `README.md` → Kafka Integration.
- Domain concepts: Entitlement (tenant's right to an application + modules), Application, Module, Flow Execution (DB-tracked), Stage Context (passed to each stage's `execute`).

## Pitfalls

- API Gateway route lifecycle is wider than create/revoke: routes are created on a module's first entitlement (`ApiGatewayModuleRouteCreator`), rotated on upgrade (`ApiGatewayModuleRouteUpdater`), and deleted once no tenant is entitled: on revoke (`ApiGatewayModuleRouteCleaner`), on upgrade of an old or removed module, and on entitle rollback with purge. A change to route logic must cover all of these paths.
- Keep stages idempotent: `@FolioModuleCallsRetryable` and `@KeycloakCallsRetryable` re-run a stage's `execute` on failure, and a new request repeats a failed flow.
- `ThreadLocalModuleStageContext` gives the retry policy access to the current stage so it can record retry errors: a stage with a retry annotation sets it at the start of `execute`, and `DatabaseLoggingStage` clears it when it records the stage status. A retried call on another thread loses these details.
- Cancellation: token refresh is automatic; a stage that changes external state during entitlement implements `cancel`, so `ignoreErrors=false` can roll it back (see `FolioModuleInstaller`; the README FAQ lists which flows support rollback).
- Do not edit generated DTOs/API interfaces in `target/generated-sources/`.
- Liquibase `changelog/changelog-master.xml`: note the mixed directory naming (`changes/v1.0.0/`, `changes.v3.0.0/`); follow the naming of the newest directory when adding changesets.
- Kafka topic names are prefixed with `ENV`.
- `KONG_*` env vars and `application.kong.*` properties are deprecated aliases that still bind via fallback expressions in `application.yml` and log a startup `WARN`; new code must use `APIGW_*` / `application.apigw.*` names.

## Testing

`@UnitTest` (Mockito); `@IntegrationTest` extends `BaseIntegrationTest` (Testcontainers + WireMock), which already carries `@EnableApiGateway` (Kong container). APISIX ITs additionally use `@EnableApisixGateway` (etcd + `folioci/folio-apisix:latest`; the image is amd64-only; on arm64 set `TESTCONTAINERS_APISIX_IMAGE` to a locally built image or rely on emulation). See `ApisixRegistrationIT`, `ApisixRouteManagementIT`.

## Key env vars

`MT_CLIENT_URL`, `AM_CLIENT_URL`, `APIGW_URL`; toggles `APIGW_ENABLED` / `KC_INTEGRATION_ENABLED` / `APIGW_ROUTEMANAGEMENT_ENABLED` (default `true`) / `APIGW_TENANT_CHECKS_ENABLED` (default `false`); `FLOW_ENGINE_*` (threads, timeout, cache); `VALIDATION_INTERFACE_*`; `ENV` (Kafka topic prefix). Full list in `README.md`.

Resources: `README.md` (env vars), `NEWS.md` (release notes).
