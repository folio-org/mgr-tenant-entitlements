---
feature_id: platform-entitlement-lock
title: Platform Operation Lock
updated: 2026-11-16
---

# Platform Operation Lock

## What it does
Platform and tenant-scoped entitlement operations never run at the same time. Whichever side finds the other busy is
rejected immediately with `409 Conflict` and a `Retry-After` header:
- While a platform operation runs, every tenant-scoped operation and every other platform operation is rejected.
- While a tenant-scoped flow is not finished, a platform operation is rejected. Tenant-scoped operations never reject
  each other.

The module never waits or retries internally; the caller retries.

## Why it exists
A platform operation changes entitlements that every tenant depends on and checks versions against all tenants. Tenant
changes made at the same time could invalidate that check, in either order: a tenant install that depends on
`platform-app ^1` must not be running while `platform-app` is upgraded to v2.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| POST | /entitlements/platform | Takes the lock for the whole operation. Fails if a tenant flow is not finished |
| PUT | /entitlements/platform | Takes the lock for the whole operation. Fails if a tenant flow is not finished |
| POST | /entitlements | Tenant entitle. Fails if the lock is held |
| PUT | /entitlements | Tenant upgrade. Fails if the lock is held |
| DELETE | /entitlements | Tenant revoke. Fails if the lock is held |
| PUT | /entitlements/state | Tenant desired state. Fails if the lock is held |

## Business rules and constraints
- A platform operation takes an exclusive, non-blocking lock before its flow is created and releases it on every
  outcome, success or failure.
- Tenant operations only check whether the lock is held. They never take it, so they do not block each other.
- After it holds the lock, a platform operation checks the `flow` table. If any flow with a tenant is not in a terminal
  status, it is rejected before its own flow is created.
- Terminal statuses are `FINISHED`, `FAILED`, `CANCELLED` and `CANCELLATION_FAILED`. `QUEUED` and `IN_PROGRESS` are not
  terminal.
- A tenant flow checks the lock again in `FlowInitializer`, right after its flow row is committed as `IN_PROGRESS`.
  A platform operation that took the lock earlier is detected there, and one that takes it later sees the row. The
  tenant flow then fails instead of running next to the platform operation.
- A request rejected by the first check creates no flow and changes no state. A request rejected by the second check
  already has its flow row, which the flow's standard finalizers move to a terminal status: `CANCELLED` by default,
  `FAILED` when the request has `ignoreErrors=true`. It does not stay `IN_PROGRESS`, so it does not block platform
  operations.
- The lock is deployment-wide: it is shared by all instances of the module that use the same database.

## Error behavior
- `409` with the `Retry-After` header in seconds. The message is `A platform entitlement operation is in progress, retry later` for
  a tenant call, or a platform call that finds another platform operation, and
  `A tenant entitlement operation is in progress, retry later` for a platform call that finds an unfinished tenant flow. In both
  cases the error type is `EntitlementFlowInProgressException`.
- Retrying after the `Retry-After` interval is the caller's responsibility.

## Configuration
| Variable | Purpose |
|----------|---------|
| application.entitlement-lock.retry-after-seconds / ENTITLEMENT_LOCK_RETRY_AFTER_SECONDS | Value of the `Retry-After` header in seconds. Default `15` |

## Dependencies and interactions
Uses a transaction-level PostgreSQL advisory lock on a connection from the module's main connection pool, so it is
shared across instances. PostgreSQL releases the lock when the transaction ends or the connection is lost, so it cannot
stay behind. One pool connection is held, idle in a transaction, while a platform operation runs. Tenant operations
probe the lock with a shared advisory lock that lives for one statement, so a platform request can briefly get `409`
while a tenant probe runs. Check that the database's `idle_in_transaction_session_timeout` is longer than the longest
platform operation, otherwise PostgreSQL ends the lock transaction early.
