## v1.3.1 2025-02-03

* KEYCLOAK-37 Add support for Keycloak v26.1.0 ([KEYCLOAK-37](https://issues.folio.org/browse/KEYCLOAK-37))

---

## v1.3.0 2024-09-30

* [APPPOCTOOL-27](https://issues.folio.org/browse/APPPOCTOOL-27) Disable tenant matching validation (#135)
* [MODCONSKC-7](https://issues.folio.org/browse/MODCONSKC-7) Move system user publisher stage before module installer… (#136)
* [APPPOCTOOL-28](https://issues.folio.org/browse/APPPOCTOOL-28) Use folio-auth-openid library for JWT validation (#137)

---

## v1.2.4 2024-08-14

[MGRENTITLE-63](https://issues.folio.org/browse/MGRENTITLE-63) Use metadata field instead of removed extensions (#119)
[APPPOCTOOL-25](https://issues.folio.org/browse/APPPOCTOOL-25) Adjust test after resource creation filter updated (#118)
[MODROLESKC-200](https://issues.folio.org/browse/MODROLESKC-200) Implement application version upgrades for capability events (#116)
[MGRENTITLE-38](https://issues.folio.org/browse/MGRENTITLE-38) Improve documentation for entitlement query parameters (#114)
[MGRAPPS-23](https://issues.folio.org/browse/MGRAPPS-23) Use extensions field to generate system user events (#115)
[MGRENTITLE-62](https://issues.folio.org/browse/MGRENTITLE-62) Fix ApplicationFlowValidator to forbid installing lower versions (#112)

---

## v1.2.3 2024-07-10

* [KONG-10](https://issues.folio.org/browse/KONG-10): upgrade kong version (#108)

---

## v1.2.1 2024-06-20

* [MODSCHED-8](https://issues.folio.org/browse/MODSCHED-8): extract SemverUtils to folio-common (#96)
* [RANCHER-1502](https://issues.folio.org/browse/RANCHER-1502): mgr-tenant-entitlements (#99)
* Configuration parameter names fixed. (#97)
* Added TLS support for FolioClientConfigurationProperties (#100)

---

## v1.2.0 2024-05-25

* [MGRENTITLE-54](https://issues.folio.org/browse/MGRENTITLE-54): Keycloak client: support TLS certificates issued by trusted certificate authorities (#88)
* [MGRENTITLE-52](https://issues.folio.org/browse/MGRENTITLE-52): add HTTPS access to application-manager (#87)
* [MGRENTITLE-51](https://issues.folio.org/browse/MGRENTITLE-51): Implement upgrade operation for modules in folio flow (#82)
* [MGRENTITLE-48](https://issues.folio.org/browse/MGRENTITLE-48): add HTTPS access to mgr-tenants (#77)
* [MGRENTITLE-43](https://issues.folio.org/browse/MGRENTITLE-43): add HTTPS access to Kong (#83)
* [MGRENTITLE-42](https://issues.folio.org/browse/MGRENTITLE-42): Create a docker file for the mgr-tenant-entitlements module that is based on the FIPS-140-2 compliant base image (ubi9/openjdk-17-runtime) (#91)
* [MGRENTITLE-39](https://issues.folio.org/browse/MGRENTITLE-39): Implement support for upgrade operation (#71)
* [MGRENTITLE-37](https://issues.folio.org/browse/MGRENTITLE-37): Secure mgr-tenant-entitlements HTTP end-points with SSL (#90)
* [MGRENTITLE-23](https://issues.folio.org/browse/MGRENTITLE-23): Implement upgrade event for system users (#81)
* [MGRENTITLE-22](https://issues.folio.org/browse/MGRENTITLE-2): Implement upgrade event for Capability entity (#79)
* [MGRENTITLE-21](https://issues.folio.org/browse/MGRENTITLE-21): Implement upgrade for scheduled job event (#78)
* [MGRENTITLE-20](https://issues.folio.org/browse/MGRENTITLE-20): Implement upgrade operation for Kong routes (#73)

---

## v1.1.0 2024-04-16

* [MGRENTITLE-40](https://issues.folio.org/browse/MGRENTITLE-40): Detached module entities are tried to update with null values (#67)
* [EUREKA-66](https://issues.folio.org/browse/EUREKA-66): Include timer interface endpoint into account in integration test asserts (#69)
* [MGRENTITLE-19](https://issues.folio.org/browse/MGRENTITLE-19): Implement upgrade operation for Keycloak service (#65)
* [APPPOCTOOL-10](https://issues.folio.org/browse/APPPOCTOOL-10): update Keycloak-related tests (#61)
* [KONG-6](https://issues.folio.org/browse/ISSUE_NUMBER): Kong timeouts should be extended (#58)
