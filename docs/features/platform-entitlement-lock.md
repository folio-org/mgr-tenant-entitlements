---
feature_id: platform-entitlement-lock
title: Platform Operation Lock
updated: 2026-11-12
---

# Platform Operation Lock

## What it does
While a platform entitlement operation runs, every tenant-scoped entitlement operation and every other platform
operation is rejected immediately with `409 Conflict` and a `Retry-After` header. The module never waits or retries
internally; the caller retries.

## Why it exists
A platform operation changes entitlements that every tenant depends on and checks versions against all tenants. Tenant
changes made at the same time could invalidate that check, so the two kinds of operations must not overlap.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| POST | /entitlements/platform | Takes the lock for the whole operation |
| PUT | /entitlements/platform | Takes the lock for the whole operation |
| POST | /entitlements | Tenant entitle. Fails if the lock is held |
| PUT | /entitlements | Tenant upgrade. Fails if the lock is held |
| DELETE | /entitlements | Tenant revoke. Fails if the lock is held |
| PUT | /entitlements/state | Tenant desired state. Fails if the lock is held |

## Business rules and constraints
- A platform operation takes an exclusive, non-blocking lock before its flow is created and releases it on every
  outcome, success or failure.
- Tenant operations only check whether the lock is held. They never take it, so they do not block each other.
- A rejected request creates no flow and changes no state.
- The lock is deployment-wide: it is shared by all instances of the module that use the same database.

## Error behavior
- `409` with message `A platform operation is in progress, retry later` and the `Retry-After` header in seconds.
- Returned to a platform call when another platform operation holds the lock, and to a tenant call when a platform
  operation holds it.
- Retrying after the `Retry-After` interval is the caller's responsibility.

## Configuration
| Variable | Purpose |
|----------|---------|
| application.platform-lock.retry-after-seconds / PLATFORM_LOCK_RETRY_AFTER_SECONDS | Value of the `Retry-After` header in seconds. Default `5` |

## Dependencies and interactions
Uses the module's PostgreSQL database for the lock, through a separate small connection pool, so it is shared across
instances.
