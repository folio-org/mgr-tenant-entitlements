## Version `v3.1.8` (15.10.2025)
* Add support for custom Keycloak base URL for JWKS endpoint, new ENV variable `KC_JWKS_BASE_URL` (MODSIDECAR-148)
* bump up applications-poc-tools dependencies to 3.0.8

## Version `v3.1.7` (26.09.2025)
* Use SECURE_STORE_ENV, not ENV, for secure store key (MGRENTITLE-139)
* Add support for custom Keycloak base URL for JWKS endpoint, new ENV variable `KC_JWKS_BASE_URL` (MODSIDECAR-148)

---

## Version `v3.1.6` (21.08.2025)
* Upgrade to Keycloak 26.3.3 (KEYCLOAK-56)

---

## Version `v3.1.5` (21.08.2025)
* Validate that upgrade of application does not affect other installed applications (MGRENTITLE-68)
* Concurrent Kafka topic creation at application flow level causes intermittent errors (MGRENTITLE-135)

---

## Version `v3.1.4` (12.08.2025)
* Reinstall endpoint responding with 403 error (MGRENTITLE-134)

---

## Version `v3.1.3` (11.07.2025)
* Update applications-poc-tools version to 3.0.2 to resolve issue with patch part comparison in semver4j

---

## Version `v3.1.2` (07.07.2025)
* Enable security by default (MGRENTITLE-104)

---

## Version `v3.1.1` (14.05.2025)
* Not able to entitle application due to dependency issue (MGRENTITLE-118)
* mgr-tenant-entitlements does not check the cross-application module dependencies as expected (MGRENTITLE-113)

---

## Version `v3.1.0` (09.04.2025)
* Reinstall API endpoints (MGRENTITLE-103)
* Permission mappings for mod-patron-blocks (MGRENTITLE-107)

---

## Version `v3.0.0` (11.03.2025)
* Upgrade Java to v21. (MGRENTITLE-102)
* Remove Kong routes management for entitled modules (MGRENTITLE-92)
* Extend GET /entitlements with ability to retrieve tenant entitlements by tenant name via additional "tenant" query parameter (MGRENTITLE-101)

---

## Version `v2.0.0` (01.11.2024)
* Remove routes while purge=false (MGRENTITLE-75)
* Implement retries for external calls (Kong, Keycloak, FOLIO Modules) (MGRENTITLE-72)
* Increase keycloak-admin-client to v25.0.6 (KEYCLOAK-24)

---

## Version `v1.3.0` (30.09.2024)
* Disable tenant matching validation (APPPOCTOOL-27)
* Move system user publisher stage before module installer… (MODCONSKC-7)
* Use folio-auth-openid library for JWT validation (APPPOCTOOL-28)

---

## Version `v1.2.4` (14.08.2024)
* Use metadata field instead of removed extensions (MGRENTITLE-63)
* Adjust test after resource creation filter updated (APPPOCTOOL-25)
* Implement application version upgrades for capability events (MODROLESKC-200)
* Improve documentation for entitlement query parameters (MGRENTITLE-38)
* Use extensions field to generate system user events (MGRAPPS-23)
* Fix ApplicationFlowValidator to forbid installing lower versions (MGRENTITLE-62)

---

## Version `v1.2.3` (10.07.2024)
* upgrade kong version (KONG-10)

---

## Version `v1.2.1` (20.06.2024)
* extract SemverUtils to folio-common (MODSCHED-8)
* mgr-tenant-entitlements (RANCHER-1502)
* Configuration parameter names fixed.
* Added TLS support for FolioClientConfigurationProperties

---

## Version `v1.2.0` (25.05.2024)
* Keycloak client: support TLS certificates issued by trusted certificate authorities (MGRENTITLE-54)
* add HTTPS access to application-manager (MGRENTITLE-52)
* Implement upgrade operation for modules in folio flow (MGRENTITLE-51)
* add HTTPS access to mgr-tenants (MGRENTITLE-48)
* add HTTPS access to Kong (MGRENTITLE-43)
* Create a docker file for the mgr-tenant-entitlements module that is based on the FIPS-140-2 compliant base image (ubi9/openjdk-17-runtime) (MGRENTITLE-42)
* Implement support for upgrade operation (MGRENTITLE-39)
* Secure mgr-tenant-entitlements HTTP end-points with SSL (MGRENTITLE-37)
* Implement upgrade event for system users (MGRENTITLE-23)
* Implement upgrade event for Capability entity (MGRENTITLE-22)
* Implement upgrade for scheduled job event (MGRENTITLE-21)
* Implement upgrade operation for Kong routes (MGRENTITLE-20)

---

## Version `v1.1.0` (16.04.2024)
* Detached module entities are tried to update with null values (MGRENTITLE-40)
* Include timer interface endpoint into account in integration test asserts (EUREKA-66)
* Implement upgrade operation for Keycloak service (MGRENTITLE-19)
* update Keycloak-related tests (APPPOCTOOL-10)
* Kong timeouts should be extended (KONG-6)
