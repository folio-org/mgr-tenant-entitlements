# mgr-tenant-entitlements

Entitles, upgrades, and revokes applications for a tenant. Each request runs as a `folio-flow-engine` flow with a nested flow per application and per module; module stages call Keycloak, the API gateway, the module `_tenant` API, and Kafka.

## Commands

```bash
mvn clean install -DskipTests  # build
mvn test                       # unit tests (@UnitTest, *Test.java)
mvn verify                     # unit and integration tests (@IntegrationTest, *IT.java); needs Docker
mvn test -Dtest=ModuleDescriptorTest#moduleDescriptor_positive_publishedEventsDeclared  # one unit test
mvn verify -Dit.test=ActuatorIT -Dtest=SkipSurefire -Dsurefire.failIfNoSpecifiedTests=false  # one IT, unit tests skipped
mvn clean install -Pcoverage   # coverage report in target/site/jacoco-aggregate/index.html
mvn checkstyle:check           # style only
```

Surefire runs only the `unit` tag and failsafe only the `integration` tag, so a test class without `@UnitTest` or `@IntegrationTest` never runs.

Checkstyle also runs at `process-classes`, so every build above fails on a violation, test sources included. The base rules come from `folio-java-checkstyle`; this repository limits methods to 23 lines (`checkstyle/checkstyle-checker.properties`) and suppresses only that limit for tests.

`BaseIntegrationTest` starts PostgreSQL, Kafka, and Kong with Testcontainers. `ApisixRegistrationIT` and `ApisixRouteManagementIT` also start APISIX and etcd (`@EnableApisixGateway`). The `folioci/folio-apisix` image is amd64-only: on arm64, rely on emulation or set `TESTCONTAINERS_APISIX_IMAGE`.

## Conventions

- The OpenAPI spec `src/main/resources/swagger.api/mgr-tenant-entitlements.yaml` generates `org.folio.entitlement.rest.resource` and `org.folio.entitlement.domain.dto`. Change the spec, not the generated sources.
- New Liquibase changesets go to `src/main/resources/changelog/changes.v<major>.0.0/` and are included from `changelog-<major>.0.0.xml`. Do not copy the older `changes/v1.0.0/` layout.
- A new flow factory applies `FlowFinalizerCallbacks` or `ApplicationFlowFinalizerCallbacks` to its builder, because without them failed and cancelled flows never get their final status written.
- Event identifiers in `src/main/resources/descriptors/ModuleDescriptor.json` are the `KafkaEventUtils.PRODUCED_EVENT_*` and `CONSUMED_EVENT_RESOURCE_RESULT` constants, not topic names; change them together. The system-user, capability, and scheduled-job topics each carry CREATE, UPDATE, and DELETE, so their identifiers have no CRUD suffix. `ModuleDescriptorTest` asserts exactly four produced events, one optional consumed `resource-result` event, and the `<module>.<resource>[.<event-type>].vN` shape.
- A stage with `@FolioModuleCallsRetryable` or `@KeycloakCallsRetryable` calls `threadLocalModuleStageContext.set(context)` at the start of `execute`, because the retry policy reads the stage context from that ThreadLocal to save retry errors on the stage row. Without it, retries are only logged. `DatabaseLoggingStage` clears the ThreadLocal when it records the stage status.
- Use `APIGW_*` and `application.apigw.*` names. The `KONG_*` and `application.kong.*` aliases still bind through fallbacks in `application.yml`; `DeprecatedKongPropertiesPostProcessor` in `folio-integration-kong` logs a startup warning for them.

## Gotchas

- Only entitle (`POST /entitlements`) and desired state (`POST /entitlements/state`) honor `ignoreErrors`. `EntitlementController` sets `ignoreErrors=true` for upgrade and revoke, so they never roll back, and the `ignoreErrors` parameter of `DELETE /entitlements` has no effect.
- The spec and the README FAQ say that a rollback (`ignoreErrors=false`) removes gateway routes and Keycloak resources. The code removes them only with `purgeOnRollback=true` (`ApiGatewayModuleRouteCreator.cancel`, `KeycloakModuleResourceCreator.cancel`). Rollback always deletes the `entitlement`, `entitlement_module`, and dependency rows, publishes an entitlement REVOKE event, and deletes the tenant topics the flow created. Without `purgeOnRollback`, it calls a module's `_tenant` API only for legacy tenant API versions (`FolioModuleService.disable`). System-user, capability, and scheduled-job events are never compensated, and gateway errors during rollback are logged and ignored.
- `ApplicationFlowValidator` rejects a request when the application's last flow has another type and is queued, in progress, failed, or cancelled. After a failed or rolled-back entitle, only a new entitle is accepted. That retry, and the retry annotations, re-run stages against partly applied state, so keep stages idempotent.
- Module `_tenant` calls are retried on any HTTP status >= 400, including 4xx, but not on connection errors or timeouts, because `FolioTenantApiClient` throws those without a status. Keycloak calls are retried only on 5xx. `RETRIES_MODULE_MAX` and `RETRIES_KEYCLOAK_MAX` count attempts, not retries (`RetryConfiguration`).
- Gateway route stages decide "first tenant" and "last tenant" from `entitlement_module` rows, which `FolioModuleService` writes in `FolioModuleInstaller` and deletes in `FolioModuleUninstaller`. Keep `ApiGatewayModuleRouteCreator` before the installer and `ApiGatewayModuleRouteCleaner` after the uninstaller. Routes are also deleted by `ApiGatewayModuleRouteUpdater` for modules removed or replaced in an upgrade, and by the creator's rollback with `purgeOnRollback=true`. A route change must cover all of these paths.
- Revoke publishes only the entitlement event. The system-user, capability, and scheduled-job DELETE events are sent only by upgrade, for modules removed from the application.
- Revoke with `purge=false` deletes the entitlement rows but does not call a module that uses tenant API 2.0, so the module keeps its data.
- With `EVENT_PUBLISHER_AWAIT_COMPLETION=true`, the system-user, capability, and scheduled-job publisher stages and their flows stay `IN_PROGRESS` until a `resource-result` event with the stage id arrives. The finalizers still save or delete entitlement records at once. A failure result, or no result within `ASYNC_CONFIRMATION_TIMEOUT`, only marks the flow `FAILED`; nothing is rolled back.
