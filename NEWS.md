## Version `v2.0.9` (26.03.2025)
* Add permission mappings for mod-patron-blocks (MGRENTITLE-107)

## Version `v2.0.8` (06.02.2025)
* Dependabot dependency updates
* Store retries information in DB (MGRENTITLE-76)
* Added logic to create resources for endpoints that do not exist in the mod-pub-sub module but are still required. Implemented mapping of Pub/Sub perm$
* Added the missing mapping for the circulation module (MGRENTITLE-89)
* Added feature toggle for disabling modules route management in Kong (MGRENTITLE-81)

## Version `v2.0.7` (30.01.2025)
* Update application-poc-tools to increase keycloak-admin-client to v26.0.4 (MGRENTITLE-91)

## Version `v2.0.6` (24.01.2025)
* Introduced cache for Keycloak auth token (MGRENTITLE-95 / EUREKA-605)

## Version `v2.0.5` (17.01.2025)
* Dependabot dependency updates
* Store retries information in DB (MGRENTITLE-76)
* Added logic to create resources for endpoints that do not exist in the mod-pub-sub module but are still required. Implemented mapping of Pub/Sub permissions to endpoints from various modules, ensuring they are secured with permissions defined in the Pub/Sub module (MODROLESKC-233)
* Added the missing mapping for the circulation module (MGRENTITLE-89)
* Added feature toggle for disabling modules route management in Kong (MGRENTITLE-81)

## Version `v2.0.4` (19.12.2024)
* Added the missing mapping for the circulation module. (MGRENTITLE-89)
* Application can't be enabled after upgrade - dependency issue (MGRENTITLE-84)
* Store retries information in DB and allow retrieval via flow details API (MGRENTITLE-76)

## Version `v2.0.3` (29.11.2024)
* Revert the changes for dummy capabilities (MODROLESKC-233)

## Version `v2.0.2` (21.11.2024)
* Application can't be enabled after upgrade (MGRENTITLE-84)
* Add additional properties to configure validators

## Version `v2.0.1` (15.11.2024)
* Removed the validator that was skipping all endpoints… (MODROLESKC-233)

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
