---
feature_id: api-gateway-route-management
title: API Gateway Route Management
updated: 2026-08-19
---

# API Gateway Route Management

## What it does

Automatically creates, updates, and deletes API Gateway services and routes as modules are entitled, upgraded, and revoked for tenants. When a module is first entitled across all tenants its routes are registered in the gateway; when the last tenant revokes it the service and routes are removed. Optionally, per-tenant header filters are added to or removed from routes when tenant isolation is enabled.

## Why it exists

Without this integration, API gateway routes for module descriptors had to be managed externally (by `mgr-applications`). Moving route lifecycle ownership into this module ensures gateway state stays consistent with entitlement state: routes exist if and only if at least one tenant is entitled to the module, and upgrades atomically retire old routes as new ones are created.

## Entry point(s)

Triggered as a parallel stage within the existing entitlement flows (defined in `src/main/resources/swagger.api/mgr-tenant-entitlements.yaml`):

| Method | Path | Operation |
|--------|------|-----------|
| POST | `/entitlements` | Entitle — creates gateway service and routes on first entitlement of the module |
| PUT | `/entitlements` | Upgrade — updates the service URL; rotates routes when the module version changes |
| DELETE | `/entitlements` | Revoke — deletes service and routes when no tenant remains entitled to the module |

## Business rules and constraints

- A gateway service and its routes are created only on the **first entitlement** of a given `moduleId` across all tenants. If other tenants are already entitled, the service is upserted (URL updated) but existing routes are not duplicated.
- On revoke, the service and routes are deleted only when **no other tenant** is entitled to the module. If other tenants are still entitled, the service and routes remain.
- On upgrade, if the module version changed (new `moduleId`):
  - Routes for the new version are created if this is its first entitlement.
  - If the old module version is no longer used by any tenant after the upgrade, its service and routes are deleted.
  - If a module is present in the old app version but absent from the new one (deleted module), its service and routes are cleaned up when no remaining entitlement exists.
- When `APIGW_TENANT_CHECKS_ENABLED=true`, a per-tenant header filter is added to every route on entitle/upgrade and removed on revoke; when `false` (default), routes are shared across tenants with no header filtering.
- Route management can be disabled entirely via `APIGW_ROUTEMANAGEMENT_ENABLED=false`, leaving routes unchanged regardless of entitlement operations (for environments where routes are externally managed).
- When `APIGW_ENABLED=false` the entire integration is skipped; no gateway calls are made.
- On entitle rollback with purge enabled: if no other entitlement exists for the module, the service and routes created during that attempt are deleted.

## Error behavior

- Gateway API errors during route creation, update, or deletion propagate as stage failures in the flow record. The flow can be retried; all gateway operations (service upsert, route creation/deletion) are idempotent.

## Configuration

| Variable | Purpose |
|----------|---------|
| `APIGW_URL` (`application.apigw.url`) | API Gateway admin URL (required when integration is enabled) |
| `APIGW_ENABLED` (`application.apigw.enabled`, default `true`) | Enables or disables the entire API Gateway integration |
| `APIGW_ROUTEMANAGEMENT_ENABLED` (`application.apigw.route-management.enabled`, default `true`) | Controls whether routes are created/deleted from module descriptors; set to `false` to let an external system own routes |
| `APIGW_TENANT_CHECKS_ENABLED` (`application.apigw.tenant-checks.enabled`, default `false`) | When `true`, adds per-tenant header filters to routes; when `false`, routes are shared across all tenants |
| `APIGW_REGISTER_MODULE` (`application.apigw.register-module`, default `true`) | Whether this service itself is registered in the gateway on startup |
| `APIGW_CONNECT_TIMEOUT` (`application.apigw.connect-timeout`) | Gateway connection timeout in milliseconds |
| `APIGW_READ_TIMEOUT` (`application.apigw.read-timeout`, default `360000`) | Gateway read timeout in milliseconds |
| `APIGW_WRITE_TIMEOUT` (`application.apigw.write-timeout`) | Gateway write timeout in milliseconds |
| `APIGW_RETRIES` (`application.apigw.retries`) | Number of retries on proxy failure |
| `APIGW_TLS_ENABLED` (`application.apigw.tls.enabled`, default `false`) | Enables TLS for connections to the gateway |
| `APIGW_TLS_TRUSTSTORE_PATH` | Path to the TLS truststore |
| `APIGW_TLS_TRUSTSTORE_PASSWORD` | Password for the TLS truststore |
| `APIGW_TLS_TRUSTSTORE_TYPE` | Type of the TLS truststore |

## Dependencies and interactions

- **API Gateway (Kong)**: All route and service lifecycle operations are performed against the gateway admin API at `APIGW_URL`. The gateway must be reachable for entitlement operations to succeed when `APIGW_ENABLED=true`.
