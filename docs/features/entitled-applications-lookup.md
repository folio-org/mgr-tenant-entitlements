---
feature_id: entitled-applications-lookup
title: Entitled Applications Lookup
updated: 2026-11-09
---

# Entitled Applications Lookup

## What it does

Returns the application descriptors, including their module and UI module descriptors, of the applications currently entitled to a tenant identified by its name, as a paged list. The tenant is resolved through mgr-tenants and the descriptors are loaded from mgr-applications. To keep the endpoint cheap under frequent calls, resolved tenants and loaded application descriptors are cached in memory; the list of entitled applications itself is always read from the current entitlement records.

## Why it exists

The endpoint is called often and transfers a substantial amount of data, and each call fans out to mgr-tenants and mgr-applications. Caching the data from those two services reduces the number of downstream calls without delaying the visibility of entitlement changes (MGRENTITLE-87).

## Entry point(s)

REST (defined in `src/main/resources/swagger.api/mgr-tenant-entitlements.yaml`):

| Method | Path                                      | Description                                                                                                                                                                                                           |
|--------|-------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| GET    | `/entitlements/{tenantName}/applications` | Returns a page of application descriptors entitled for the tenant. Query parameters: `limit` (0-500, default 10), `offset` (default 0). Header `x-okapi-tenant` is required, `x-okapi-token` is optional in the spec. |

The module descriptor declares the endpoint as `internal` with no required permissions (`permissionsRequired: []`).

## Business rules and constraints

- The set of entitled applications is read from the entitlement records on every request and is never cached, so an entitle, upgrade or revoke is reflected by the next request.
- `totalRecords` is the number of applications entitled to the tenant. `limit` and `offset` are applied to the loaded list, so `totalRecords` does not depend on the page size.
- A tenant with no entitlements returns an empty list with `totalRecords` `0`.
- Descriptors are loaded with module descriptors included. With the caches active the list is ordered by application id; without them it keeps the order returned by mgr-applications.
- The tenant name in the path is not compared with the `x-okapi-tenant` header or with the tenant of the caller's token.
- Calls to mgr-tenants and mgr-applications are made with the module's own system token when Keycloak integration is enabled, and with the caller's token otherwise.
- Caches are used only when the downstream calls are made under the system token. If the caller's own token is forwarded (Keycloak integration disabled), both caches are bypassed, so mgr-tenants and mgr-applications authorize every request individually.
- The entitlement flows (entitle, upgrade, revoke, desired state) do not use these caches and always read current data from mgr-tenants and mgr-applications.

## Error behavior

- Tenant not found by name, or more than one tenant matching the name: `404 Not Found`.
- One or more entitled applications are not found in mgr-applications: `400 Bad Request` (validation error listing the missing application ids).
- A failed call to mgr-tenants or mgr-applications: `400 Bad Request`, error `code` `service_error`.
- Failed lookups and errors are never cached; the next request calls the downstream service again.

## Caching

Two in-memory caches are kept per module instance; there is no invalidation between instances, so entries are refreshed only when they expire.

| Cache                    | Key            | Value                                                                |
|--------------------------|----------------|----------------------------------------------------------------------|
| `tenant-id`              | Tenant name    | Tenant (id and name) resolved from mgr-tenants                       |
| `application-descriptor` | Application id | Application descriptor with module descriptors from mgr-applications |

- Only descriptors that are not cached are requested from mgr-applications, in batches.
- A tenant whose returned name differs from the requested name is not cached.
- A tenant that is deleted and re-created under the same name gets a new id. If no entitlements are found for the cached id, the tenant is resolved again once; otherwise a stale tenant id can be served until the entry expires.
- An application descriptor replaced in mgr-applications under the same application id can be served stale until the entry expires.
- Set a time to live to `0` to disable the corresponding cache.
- The Keycloak system token used for downstream calls is cached separately (`access-token`) as a single entry shared by all callers until near its expiration.

## Configuration

| Variable                                                                                                    | Purpose                                                                             |
|-------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------|
| `TENANT_ID_CACHE_TTL` (`application.cache.tenant-id.ttl`, default `15m`)                                    | Time to live of cached tenants                                                      |
| `TENANT_ID_CACHE_MAX_SIZE` (`application.cache.tenant-id.max-size`, default `100`)                          | Maximum number of cached tenants                                                    |
| `APP_DESCRIPTOR_CACHE_TTL` (`application.cache.application-descriptor.ttl`, default `10m`)                  | Time to live of cached application descriptors                                      |
| `APP_DESCRIPTOR_CACHE_MAX_SIZE` (`application.cache.application-descriptor.max-size`, default `300`)        | Maximum number of cached application descriptors                                    |
| `KC_AUTHORIZATION_CACHE_TTL_OFFSET` (`application.keycloak.authorization-cache-ttl-offset`, default `5000`) | How many milliseconds before its expiration a cached system token is dropped        |
| `KC_INTEGRATION_ENABLED` (`application.keycloak.enabled`)                                                   | Enables the system token and, with it, the tenant and application descriptor caches |

## Dependencies and interactions

- Depends on mgr-tenants to resolve the tenant by name (query by tenant name).
- Depends on mgr-applications to load application descriptors by id (queried with module descriptors included).
- Depends on Keycloak to obtain the system token for downstream calls when Keycloak integration is enabled.
