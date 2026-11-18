---
feature_id: async-tenant-job-completion
title: Async Tenant Job Completion
updated: 2026-11-18
---

# Async Tenant Job Completion

## What it does

When a FOLIO module answers its tenant API call (`POST /_/tenant`) with `201 Created` and a tenant job, the module's installer stage waits until the module reports the job as complete before the entitlement continues. A job that completes with an error, or does not complete within the configured timeout, fails the module stage with an error that names the module and the job. A module that answers `204 No Content` (or any other success without a job) is treated as done and is not polled.

## Why it exists

Modules built on the RAML Module Builder run their tenant initialization in the background and report failures only on the job. Before this behavior any successful status was accepted, so a failed initialization went unnoticed and later stages (events, dependent modules) ran against a module that was not ready (MGRENTITLE-224). Whether a call is asynchronous is decided from the response, because modules declare the same `_tenant` interface version whether or not they use jobs.

## Entry point(s)

REST requests that make the entitlement call module tenant APIs (defined in `src/main/resources/swagger.api/mgr-tenant-entitlements.yaml`):

| Method | Path | Description |
|--------|------|-------------|
| POST | `/entitlements` | Install applications; each module is installed and its job awaited |
| PUT | `/entitlements` | Upgrade applications; upgraded modules are installed and their jobs awaited |
| DELETE | `/entitlements` | Revoke applications; a module purge answered with a job is awaited |
| PUT | `/entitlements/state` | Apply a desired state; installs and purges as above |
| POST | `/reinstall/applications` | Re-run module installation for applications; jobs are awaited |
| POST | `/reinstall/modules` | Re-run module installation for modules; jobs are awaited |

The module calls that can be answered with a job:

| Type | Call | Description |
|------|------|-------------|
| Module call | `POST /_/tenant` | Install, upgrade, or purge (`purge=true`) |
| Module call | `POST /_/tenant/disable` | Legacy disable (modules on the old tenant API) |
| Module call | `GET /_/tenant/{id}?wait=<ms>` | Reads the job; the module may hold the request until the job changes or `wait` elapses (long poll) |

The legacy `DELETE /_/tenant` purge call is not polled.

## Business rules and constraints

- A tenant call is treated as asynchronous only when the module answers `201` and a job id can be found: from the `Location` header (its path must end with `/_/tenant/{id}`, id of up to 64 letters, digits or `-`), or, when there is no `Location`, from the `id` in the response body. A `201` without a usable job id is logged as a warning and treated as completed. Any other success status is treated as completed.
- The job is read until it reports `complete: true`. A completed job with an `error` fails the stage; without an `error` the stage succeeds. A job response without a `complete` flag counts as still in progress.
- Reading the job is repeated, with a growing pause between reads, until the timeout when the failure is transient (I/O error, `5xx`, or `408`, `425`, `429`). Any other `4xx` while reading the job, including `404`, fails the stage immediately. While the job is read, a warning is logged periodically when reads keep failing, and an INFO line periodically while the job is still in progress.
- The timeout is counted per module, from the moment the module accepted the job. Each read asks the module to wait for at most the configured long-poll time, never longer than the time left.
- Job failures and timeouts are not repeated by the module call retry (`RETRIES_MODULE_*`), so the tenant call is not sent again automatically. A failure of the initial `POST` itself (HTTP status `400` and above) is retried as before.
- The module entitlement is saved only after the module call and its job completed; on revoke it is deleted only after the module call (and its job) succeeded. These operations are not transactional, so no database transaction is held while waiting.
- A module whose job failed or timed out has no stored entitlement. A rollback therefore does not purge it: its tenant can stay partly initialized, or its job can still be running inside the module. The next entitlement request sends the tenant call again because the module is not recorded as installed.
- The entitlement event of a module is published by a later stage, so it is sent only after the module's job completed. The system-user event stage runs before module installation, as before.
- Modules are installed in parallel on a fixed number of installer threads; a module that waits for its job occupies one of them until the job ends. When one module's job fails or times out, its stage fails at once, but the other modules of the flow keep waiting for their jobs and the flow reports the failure after all parallel stages finish.
- After the entitlement execution timeout of a synchronous request has fired, polling already in progress is not interrupted; it ends when the job completes or when its own timeout expires.
- The tenant job is not deleted from the module after its final state is read.
- Reinstall requests are synchronous and wait for jobs, up to the timeout per module.
- The polling settings are validated when the application starts: the timeout and the maximum pause must be greater than zero, the long-poll time and the initial pause must not be negative, and the multiplier must be at least 1. An invalid value fails the module startup with an error naming the property.

## Error behavior

- A failed or timed-out job fails the module's installer stage (`<moduleId>-folioModuleInstaller`). For a synchronous request the response is `400` with the flow identifier in the `x-mgr-tenant-entitlement-flow-id` header; the failed stage is readable via `GET /entitlement-flows/{flowId}?includeStages=true`.
- The stage error type is `TenantJobFailedException` (job completed with an error), `TenantJobTimeoutException` (no completion within the timeout) or `TenantJobPollingException` (the job could not be read, or polling was interrupted). The stage error message has the form `<message>, parameters: [{key: <key>, value: <value>}, ...]` with these parameters:

| Error type | Parameters |
|------------|------------|
| `TenantJobFailedException` | `moduleId`, `tenant`, `jobId`, `cause` (the job's `error`), `messages` (the job's messages joined by `; `) |
| `TenantJobTimeoutException` | `moduleId`, `tenant`, `jobId`, `timeout`, and `lastError` (the failure of the last job read) when that read failed, which tells an unavailable module from a slow job |
| `TenantJobPollingException` | `moduleId`, `tenant`, `jobId` |

- Retrying the request after a failure is safe: no entitlement was stored for the module, so the tenant call is sent again.

## Configuration

| Variable | Purpose |
|----------|---------|
| `FOLIO_CLIENT_TENANT_JOB_TIMEOUT` (`application.clients.folio.tenant-job.timeout`, default `30m`) | Maximum time to wait for one module's tenant job to complete; must be greater than zero |
| `FOLIO_CLIENT_TENANT_JOB_WAIT` (`application.clients.folio.tenant-job.wait`, default `10s`) | Long-poll time sent as the `wait` parameter of each job read; must not be negative |
| `FOLIO_CLIENT_TENANT_JOB_DELAY` (`application.clients.folio.tenant-job.delay`, default `1s`) | Initial pause between job reads, used when the module answers immediately; must not be negative |
| `FOLIO_CLIENT_TENANT_JOB_MAX_DELAY` (`application.clients.folio.tenant-job.max-delay`, default `10s`) | Upper bound of the pause between job reads; must be greater than zero |
| `FOLIO_CLIENT_TENANT_JOB_MULTIPLIER` (`application.clients.folio.tenant-job.multiplier`, default `2.0`) | Factor applied to the pause after every job read; must be at least 1 |
| `FOLIO_CLIENT_READ_TIMEOUT` (`application.clients.folio.read-timeout`) | Read timeout of a module call; each job read allows this time plus the long-poll time |
| `FLOW_ENGINE_EXECUTION_TIMEOUT` (`application.flow-engine.execution-timeout`, default `30m`) | Timeout of a synchronous entitlement request; a warning is logged on startup when the job timeout is longer than this value |
| `FLOW_ENGINE_MODULE_INSTALLER_THREADS` (`application.flow-engine.module-installer-threads`, default `4`) | Number of modules installed in parallel; each waiting module holds one thread |

## Dependencies and interactions

- Depends on FOLIO modules' tenant API (`/_/tenant`, `/_/tenant/{id}`, and for legacy modules `/_/tenant/disable`), called at the module's discovery location with the `x-okapi-tenant` header.
- Works together with [Entitlement Execution Timeout](entitlement-execution-timeout.md): that timeout bounds a synchronous request, while the job timeout bounds the wait for one module.
- Module events are published by the stages that follow module installation in the module's flow; those stages are skipped when the installer stage fails.
