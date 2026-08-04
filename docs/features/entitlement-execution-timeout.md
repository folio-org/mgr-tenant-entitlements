---
feature_id: entitlement-execution-timeout
title: Entitlement Execution Timeout
updated: 2026-08-04
---

# Entitlement Execution Timeout

## What it does

Bounds the duration of synchronous entitlement operations (entitle, upgrade, revoke, and desired-state requests with `async=false`, the default). When an operation does not finish within the configured execution timeout, the request returns `400` with a flow identifier header, and the flow record — the flow, its application flows, and their in-progress stages — is marked `failed`. If the flow completed successfully in the same instant the timeout fired, the request is reported as successful instead.

## Why it exists

Without the bound, a synchronous entitlement job whose module call or retry loop outlived the client's patience stayed `in_progress` forever, blocking any further entitlement operation for the tenant's applications (MGRENTITLE-161). The timeout guarantees the job always reaches a terminal, retryable state and the caller gets a diagnosable response.

## Entry point(s)

REST (all accept the `async` query parameter, default `false` = synchronous; defined in `src/main/resources/swagger.api/mgr-tenant-entitlements.yaml`):

| Method | Path | Operation |
|--------|------|-----------|
| POST | `/entitlements` | Install/enable applications for a tenant |
| PUT | `/entitlements` | Upgrade applications |
| DELETE | `/entitlements` | Revoke applications |
| PUT | `/entitlements/state` | Apply a desired state of application entitlements |

The resulting flow record is readable via `GET /entitlement-flows/{flowId}` (optionally `?includeStages=true`).

## Business rules and constraints

- The timeout applies to synchronous requests only; `async=true` executions are not bounded by it.
- On expiry, the flow row is failed first, then all non-terminal application flows and in-progress stages — the record can never show a successful flow whose caller was told it failed.
- The running execution cannot be aborted immediately; it stops at the next application boundary. Work already in flight for the current application keeps running in the background but cannot change the reported statuses or create entitlements: success side effects only apply when the final status write succeeds.
- If the timeout fires before the flow was even scheduled (saturated executor), the flow record is created directly in `failed` status and the scheduled execution refuses to start.
- A rollback (`ignoreErrors=false`) may later transition the record from `failed` to `cancelled` — cancellation outcomes always reflect the real final state.
- A request that timed out can be retried: `failed` (and `cancelled`) application flows do not block a repeated request of the same type, and module installations are idempotent.

## Error behavior

- Timeout expiry: `400 Bad Request`, error `type` `FlowExecutionTimeoutException`, `code` `service_error`; parameters include any failed stages and a `timeout` entry with the configured duration; response header `x-mgr-tenant-entitlement-flow-id` carries the flow id for follow-up queries.
- Flow finished concurrently with the timeout: normal success response.
- Flow reached another terminal status concurrently (e.g. `cancelled`): `400` with the actual status in the error message.

## Configuration

| Variable | Purpose |
|----------|---------|
| `FLOW_ENGINE_EXECUTION_TIMEOUT` (`application.flow-engine.execution-timeout`, default `30m`) | Maximum duration of a synchronous entitlement execution before it is reported as failed |
