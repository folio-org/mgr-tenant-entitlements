---
feature_id: platform-application-entitlement
title: Platform Application Entitlement
updated: 2026-11-12
---

# Platform Application Entitlement

## What it does
Platform-scoped applications are entitled and upgraded once for the whole deployment, not per tenant. `POST` entitles
a set of platform applications, and `PUT` replaces their entitled versions with higher ones. Both calls are
synchronous: when they return, the entitlement is stored and the flow has reached a terminal state. No modules are
installed for platform applications; only their entitlement and dependency records are stored.

## Why it exists
Some applications belong to the platform rather than to a tenant. They need a single deployment-wide entitlement whose
version is checked against the applications that depend on it in every tenant, instead of being entitled tenant by
tenant.

## Entry point(s)
| Method | Path | Description |
|--------|------|-------------|
| POST | /entitlements/platform | Entitles platform-scoped applications. Returns `201` with the entitlements and the `flowId` |
| PUT | /entitlements/platform | Replaces entitled versions of platform-scoped applications with higher versions. Returns `200` with the entitlements and the `flowId` |

The request body is `{ "applications": [ "<name>-<version>", ... ] }` with 1 to 50 application ids and no tenant. The
`x-okapi-token` header is accepted. The permissions are `mgr-tenant-entitlements.entitlements.platform.item.post` and
`mgr-tenant-entitlements.entitlements.platform.item.put`.

## Business rules and constraints
- The request is atomic: if any application is invalid, the whole request is rejected and nothing is entitled or
  changed.
- The same application name may appear only once in a request.
- Every application must be platform-scoped.
- Only one version of an application is entitled at a time.
  - Entitle is rejected while a different version of the same application is entitled. Repeating the same entitle
    request succeeds.
  - Upgrade requires an entitled version and a higher requested version. Repeating the same upgrade succeeds.
- Required dependencies of an application must already be entitled with a version in the declared range. Applications
  in the same request are processed in dependency order. Optional dependencies are not required to be entitled.
- Upgrade checks every dependent application before any change is applied. A new version is rejected if it falls outside
  the declared range of a required dependency of
  - another entitled platform application, or
  - an application entitled for any tenant.

  Optional dependencies are ignored in this check. A platform application that is upgraded in the same request is
  not checked against its previously stored dependencies; its new version is checked as part of the request.
- Dependencies declared by an application are stored whether or not the parent is entitled. This includes optional
  ones.
- Platform-scoped applications cannot be set in a tenant desired state (`PUT /entitlements/state`).

## Error behavior
- `400` with the validation message, and the flow identifier in the `x-mgr-tenant-entitlement-flow-id` response
  header. The flow ends in `cancelled`. Messages:
  - `Request contains the same application more than once`
  - `Application is not platform-scoped`
  - `Another version of the application is already entitled` (entitle)
  - `Invalid applications provided for platform upgrade` (no entitled version, or version not higher)
  - `Platform application upgrade is incompatible with dependent applications` (dependent platform applications or
    tenant applications)
  - `The following platform application dependencies must be entitled first`
  - `Desired state request contains platform-scoped applications` (desired-state write)
- `409` with `Retry-After` when the platform lock is held; see [Platform Operation Lock](platform-entitlement-lock.md).

## Dependencies and interactions
Depends on mgr-applications to read application descriptors. The module descriptor requires
`mgr-applications.applications.collection.get` and `mgr-applications.applications.item.validate` for both calls.
No tenant, discovery or module permissions are needed, since no tenant is involved and no modules are installed.
