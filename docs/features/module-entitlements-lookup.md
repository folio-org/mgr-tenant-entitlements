---
feature_id: module-entitlements-lookup
title: Module Entitlements Lookup
updated: 2026-06-17
---

# Module Entitlements Lookup

## What it does
Returns the list of tenant entitlements for a given module — i.e. which tenants (and via which
applications) currently have that module enabled. Results are paginated and sorted by tenant id.

## Why it exists
Consumers such as the module sidecar need to know which tenants have a specific module enabled (for
routing and enablement decisions) and poll this endpoint frequently. The lookup provides that
mapping directly from the entitlement records, and is backed by a server-side cache so the repeated
polling does not put read load on the database.

## Entry point(s)

REST — defined in `src/main/resources/swagger.api/mgr-tenant-entitlements.yaml`
(`getModuleEntitlements`), implemented by `EntitlementModuleController.getModuleEntitlements`.

```
GET /entitlements/modules/{moduleId}
```

| Parameter | In | Required | Default | Constraints |
|-----------|----|----------|---------|-------------|
| `moduleId` | path | yes | — | — |
| `limit` | query | no | 10 | min 0, max 500 |
| `offset` | query | no | 0 | min 0 |

Response `200`: `entitlements` object — `totalRecords` (full count for the module) and
`entitlements[]`, each with `applicationId`, `tenantId`, and `modules`.

## Business rules and constraints
- Returns one record per `(tenantId, applicationId)` pairing that has the module entitled.
- Records are ordered by `tenantId` ascending.
- `totalRecords` reflects the total number of entitlements for the module, independent of `limit`/`offset`.
- Pagination is applied over the complete per-module result set (the full list is retrieved, then the
  `[offset, offset+limit)` window is returned).

## Error behavior
- `400 Bad Request` — invalid request parameters.
- `500 Internal Server Error` — unexpected server error.

## Caching
The lookup is served from an in-memory Caffeine cache (`module-entitlements`) keyed by `moduleId`;
the full per-module entitlement list is cached and pagination is applied in memory
(`ModuleEntitlementsCacheProvider`, `EntitlementModuleService.getModuleEntitlements`).

- **Warm-up & refresh:** `ModuleEntitlementsCacheWarmer` loads all module entitlements in a single
  query at startup and on a fixed delay (`refresh-interval`), so a cold instance does not issue a
  burst of per-module queries.
- **Invalidation:** entitlement changes (entitle / upgrade / revoke) evict only the affected
  `moduleId` entries in-process; unrelated modules stay cached. `expireAfterWrite` (`ttl`) is a
  backstop kept above `refresh-interval`.
- **Cluster note:** invalidation is in-process and assumes a single running instance of
  mgr-tenant-entitlements. The cache can be disabled (`MODULE_ENTITLEMENTS_CACHE_ENABLED=false`), in
  which case every lookup reads from the database.

## Configuration
| Variable | Purpose |
|----------|---------|
| `MODULE_ENTITLEMENTS_CACHE_ENABLED` | Enables the lookup cache; when `false`, every request reads from the database and no warm-up runs. |
| `MODULE_ENTITLEMENTS_CACHE_MAX_SIZE` | Maximum number of cached per-module lists (Caffeine `maximumSize`). |
| `MODULE_ENTITLEMENTS_CACHE_TTL` | Backstop expiry of cached entries (Caffeine `expireAfterWrite`); kept above the refresh interval. |
| `MODULE_ENTITLEMENTS_CACHE_REFRESH_INTERVAL` | How often the warmer re-loads all module entitlements to keep the cache warm. |

## Dependencies and interactions
- **PostgreSQL** — reads the `entitlement_module` table (`EntitlementModuleRepository`,
  `EntitlementModuleEntity`).
