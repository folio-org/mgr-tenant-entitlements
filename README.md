# mgr-tenant-entitlements

Copyright (C) 2022-2025 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Table of contents

* [Introduction](#introduction)
* [Compiling](#compiling)
* [Running It](#running-it)
* [Environment Variables](#environment-variables)
  * [Validators environment variables](#validators-environment-variables)
  * [Kafka environment variables](#kafka-environment-variables)
  * [SSL Configuration environment variables](#ssl-configuration-environment-variables)
  * [Secure storage environment variables](#secure-storage-environment-variables)
    * [AWS-SSM](#aws-ssm)
    * [Vault](#vault)
    * [Folio Secure Store Proxy (FSSP)](#folio-secure-store-proxy-fssp)
  * [Keycloak Integration](#keycloak-integration)
    * [Import Keycloak data on startup](#import-keycloak-data-on-startup)
    * [Keycloak Security](#keycloak-security)
    * [Keycloak specific environment variables](#keycloak-specific-environment-variables)
  * [Retry configuration](#retry-configuration)
* [Kafka Integration](#kafka-integration)
  * [Events upon application being enabled/disabled](#events-upon-application-being-enableddisabled)
    * [Naming convention](#naming-convention)
    * [Event structure](#event-structure)
* [FAQ](#faq)
  * [How ignoreErrors affects tenant entitlement?](#how-ignoreerrors-affects-tenant-entitlement)
  * [How purgeOnRollback affects tenant entitlement process](#how-purgeonrollback-affects-tenant-entitlement-process)
  * [How purge flag is working for application uninstalling?](#how-purge-flag-is-working-for-application-uninstalling)
  * [Is rollbacks are supported for the application uninstalling/upgrades?](#is-rollbacks-are-supported-for-the-application-uninstallingupgrades)
  * [How async flag works?](#how-async-flag-works)

## Introduction

`mgr-tenant-entitlements` provides following functionality:

* Dependency check / platform integrity validation
* Enabling/disabling of an application (including dependencies)

## Compiling

```shell
mvn clean install
```

See that it says `BUILD SUCCESS` near the end.

If you want to skip tests:

```shell
mvn clean install -DskipTests
```

## Running It

Run locally with proper environment variables set (see [Environment variables](#environment-variables) below) on
listening port 8081 (default listening port):

```shell
java \
  -Dokapi.url=http://localhost:9130 \
  -Dam.url=http://localhost:9130 \
  -Dokapi.token=${okapiToken} \
  -jar target/mgr-tenant-entitlements-*.jar
```

Build the docker container with following script after compilation:

```shell
docker build -t mgr-tenant-entitlements .
```

Test that it runs with:

```shell
docker run \
  --name mgr-tenant-entitlements \
  --link postgres:postgres \
  -e DB_HOST=postgres \
  -e okapi.url=http://okapi:9130 \
  -e tenant.url=http://mgr-tenants:8081 \
  -e am.url=http://mgr-applications:8081 \
  -e okapi.token=${okapiToken} \
  -p 8081:8081 \
  -d mgr-tenant-entitlements
```

## Environment Variables

| Name                                   | Default value                       | Required | Description                                                                                                                                                                                                |
|:---------------------------------------|:------------------------------------|:--------:|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| DB_HOST                                | localhost                           |  false   | Postgres hostname                                                                                                                                                                                          |
| DB_PORT                                | 5432                                |  false   | Postgres port                                                                                                                                                                                              |
| DB_USERNAME                            | postgres                            |  false   | Postgres username                                                                                                                                                                                          |
| DB_PASSWORD                            | postgres                            |  false   | Postgres username password                                                                                                                                                                                 |
| DB_DATABASE                            | okapi_modules                       |  false   | Postgres database name                                                                                                                                                                                     |
| MODULE_URL                             | http://mgr-tenant-entitlements:8081 |  false   | Module URL (module cannot define url for Kong registration by itself, because it can be under Load Balancer, so this value must be provided manually)                                                      |
| okapi.url                              | -                                   |  false   | Okapi URL used to perform HTTP requests by `OkapiClient`.                                                                                                                                                  |
| tenant.url                             | -                                   |   true   | Tenant URL used to perform HTTP requests by `TenantManagerClient`.                                                                                                                                         |
| am.url                                 | -                                   |   true   | Application Manager URL used to perform HTTP requests by `ApplicationManagerClient`.                                                                                                                       |
| AM_CLIENT_TLS_ENABLED                  | false                               |  false   | Enables TLS for application-manager client.                                                                                                                                                                |
| AM_CLIENT_TLS_TRUSTSTORE_PATH          | -                                   |  false   | Truststore file path for application-manager client.                                                                                                                                                       |
| AM_CLIENT_TLS_TRUSTSTORE_PASSWORD      | -                                   |  false   | Truststore password for application-manager client.                                                                                                                                                        |
| AM_CLIENT_TLS_TRUSTSTORE_TYPE          | -                                   |  false   | Truststore file type for application-manager client.                                                                                                                                                       |
| kong.url                               | -                                   |   true   | Okapi URL used to perform HTTP requests for recurring jobs, required.                                                                                                                                      |
| KONG_ADMIN_URL                         | -                                   |  false   | Alias for `kong.url`.                                                                                                                                                                                      |
| KONG_INTEGRATION_ENABLED               | true                                |  false   | Defines if kong integration is enabled or disabled.<br/>If it set to `false` - it will exclude all kong-related beans from spring context.                                                                 |
| KONG_CONNECT_TIMEOUT                   | -                                   |  false   | Defines the timeout in milliseconds for establishing a connection from Kong to upstream service. If the value is not provided then Kong defaults are applied.                                              |
| KONG_READ_TIMEOUT                      | 360000                              |  false   | Defines the timeout in milliseconds between two successive read operations for transmitting a request from Kong to the upstream service. If the value is not provided then Kong defaults are applied.      |
| KONG_WRITE_TIMEOUT                     | -                                   |  false   | Defines the timeout in milliseconds between two successive write operations for transmitting a request from Kong to the upstream service. If the value is not provided then Kong defaults are applied.     |
| KONG_RETRIES                           | -                                   |  false   | Defines the number of retries to execute upon failure to proxy. If the value is not provided then Kong defaults are applied.                                                                               |
| KONG_TLS_ENABLED                       | false                               |  false   | Allows to enable/disable TLS connection to Kong.                                                                                                                                                           |
| KONG_TLS_TRUSTSTORE_PATH               | -                                   |  false   | Truststore file path for TLS connection to Kong.                                                                                                                                                           |
| KONG_TLS_TRUSTSTORE_PASSWORD           | -                                   |  false   | Truststore password for TLS connection to Kong.                                                                                                                                                            |
| KONG_TLS_TRUSTSTORE_TYPE               | -                                   |  false   | Truststore file type for TLS connection to Kong.                                                                                                                                                           |
| OKAPI_INTEGRATION_ENABLED              | false                               |  false   | Defines if okapi integration is enabled or disabled.<br/>If it set to `false` - it will exclude OkapiModuleInstaller stage from flow and okapi related beans from spring context.                          |
| ENV                                    | folio                               |  false   | The logical name of the deployment (kafka topic prefix), must be unique across all environments using the same shared Kafka/Elasticsearch clusters, `a-z (any case)`, `0-9`, `-`, `_` symbols only allowed |
| SECURITY_ENABLED                       | true                                |  false   | Allows to enable/disable security. If true and KC_INTEGRATION_ENABLED is also true - the Keycloak will be used as a security provider.                                                                     |
| MT_CLIENT_TLS_ENABLED                  | false                               |  false   | Allows to enable/disable TLS connection to mgr-tenants module.                                                                                                                                             |
| MT_CLIENT_TLS_TRUSTSTORE_PATH          | -                                   |  false   | Truststore file path for TLS connection to mgr-tenants module.                                                                                                                                             |
| MT_CLIENT_TLS_TRUSTSTORE_PASSWORD      | -                                   |  false   | Truststore password for TLS connection to mgr-tenants module.                                                                                                                                              |
| MT_CLIENT_TLS_TRUSTSTORE_TYPE          | -                                   |  false   | Truststore file type for TLS connection to mgr-tenants module.                                                                                                                                             |
| FOLIO_CLIENT_CONNECT_TIMEOUT           | 10s                                 |  false   | Defines the connection timeout for the Java Http client, used to perform request to Folio Modules.                                                                                                         |
| FOLIO_CLIENT_READ_TIMEOUT              | 30s                                 |  false   | Defines the read timeout for the Java Http client, used to perform request to Folio Modules.                                                                                                               |
| FOLIO_CLIENT_TLS_ENABLED               | false                               |  false   | Allows to enable/disable TLS connection to Folio Modules.                                                                                                                                                  |
| FOLIO_CLIENT_TLS_TRUSTSTORE_PATH       | -                                   |  false   | Truststore file path for TLS connection to Folio Modules.                                                                                                                                                  |
| FOLIO_CLIENT_TLS_TRUSTSTORE_PASSWORD   | -                                   |  false   | Truststore password for TLS connection to Folio Modules.                                                                                                                                                   |
| FOLIO_CLIENT_TLS_TRUSTSTORE_TYPE       | -                                   |  false   | Truststore file type for TLS connection to Folio Modules.                                                                                                                                                  |
| MOD_AUTHTOKEN_URL                      | -                                   |   true   | Mod-authtoken URL. Required if OKAPI_INTEGRATION_ENABLED is true and SECURITY_ENABLED  is true and KC_INTEGRATION_ENABLED is false.                                                                        |
| SECURE\_STORE\_ENV                     | folio                               |  false   | First segment of the secure store key, for example `prod` or `test`. Defaults to `folio`. In Ramsons and Sunflower defaults to ENV with fall-back `folio`.                                                 |
| SECRET_STORE_TYPE                      | -                                   |   true   | Secure storage type. Supported values: `EPHEMERAL`, `AWS_SSM`, `VAULT`, `FSSP`                                                                                                                             |
| MAX_HTTP_REQUEST_HEADER_SIZE           | 200KB                               |  false   | Maximum size of the HTTP request header.                                                                                                                                                                   |
| FLOW_ENGINE_EXECUTION_TIMEOUT          | 30m                                 |  false   | Maximum execution timeout for entitlement execution in sync mode.                                                                                                                                          |
| FLOW_ENGINE_LAST_EXECUTIONS_CACHE_SIZE | 25                                  |  false   | Max cache size for the latest flow executions                                                                                                                                                              |
| FLOW_ENGINE_PRINT_FLOW_RESULTS         | false                               |  false   | Defines if flow engine should print execution results in logs or not                                                                                                                                       |
| FLOW_ENGINE_THREADS_NUM                | 4                                   |  false   | Defines the number of threads for Fork-Join Pool used by flow engine.                                                                                                                                      |
| REGISTER_MODULE_IN_KONG                | true                                |  false   | Defines if module must be registered in Kong (it will create for itself service and list of routes from module descriptor)                                                                                 |
| ROUTER_PATH_PREFIX                     |                                     |  false   | Defines routes prefix to be added to the generated endpoints by OpenAPI generator (`/foo/entites` -> `{{prefix}}/foo/entities`). Required if load balancing group has format like `{{host}}/{{moduleId}}`  |

### Validators environment variables

| Name                                                     | Default value | Required | Description                                                                                                                                                                                                               |
|:---------------------------------------------------------|:--------------|:--------:|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| VALIDATION_INTERFACE_INTEGRITY_ENTITLEMENT_ENABLED       | true          |  false   | Enables Interface-Integrity validation for entitlement operations. If false, the validator will not be executed for entitlement.                                                                                          |
| VALIDATION_INTERFACE_INTEGRITY_UPGRADE_ENABLED           | true          |  false   | Enables Interface-Integrity validation for upgrade operations. If false, the validator will not be executed for upgrade.                                                                                                  |
| VALIDATION_INTERFACE_COLLECTOR_MODE                      | combined      |  false   | Defines how required/provided interfaces are collected from application descriptors for validation. `scoped` means interfaces are collected per application and dependencies, `combined` means all applications together. |
| VALIDATION_INTERFACE_COLLECTOR_EXCLUDE_ENTITLED_REQUIRED | true          |  false   | If set to `true`, required interfaces of entitled applications will not be collected.                                                                                                                                     |


### Kafka environment variables

| Name                                         | Default value | Required | Description                                                                                                                                                |
|:---------------------------------------------|:--------------|:--------:|:-----------------------------------------------------------------------------------------------------------------------------------------------------------|
| KAFKA_HOST                                   | kafka         |  false   | Kafka broker hostname                                                                                                                                      |
| KAFKA_PORT                                   | 9092          |  false   | Kafka broker port                                                                                                                                          |
| KAFKA_SECURITY_PROTOCOL                      | PLAINTEXT     |  false   | Kafka security protocol used to communicate with brokers (SSL or PLAINTEXT)                                                                                |
| KAFKA_SSL_KEYSTORE_LOCATION                  | -             |  false   | The location of the Kafka key store file. This is optional for client and can be used for two-way authentication for client.                               |
| KAFKA_SSL_KEYSTORE_PASSWORD                  | -             |  false   | The store password for the Kafka key store file. This is optional for client and only needed if 'ssl.keystore.location' is configured.                     |
| KAFKA_SSL_TRUSTSTORE_LOCATION                | -             |  false   | The location of the Kafka trust store file.                                                                                                                |
| KAFKA_SSL_TRUSTSTORE_PASSWORD                | -             |  false   | The password for the Kafka trust store file. If a password is not set, trust store file configured will still be used, but integrity checking is disabled. |
| KAFKA_ENTITLEMENT_TOPIC_PARTITIONS           | 1             |  false   | Amount of partitions for `entitlement` topic.                                                                                                              |
| KAFKA_ENTITLEMENT_TOPIC_REPLICATION_FACTOR   | -             |  false   | Replication factor for `entitlement` topic.                                                                                                                |
| KAFKA_SCHEDULED_JOB_TOPIC_PARTITIONS         | 1             |  false   | Amount of partitions for `mgr-tenant-entitlements.scheduled-job` topic.                                                                                    |
| KAFKA_SCHEDULED_JOB_TOPIC_REPLICATION_FACTOR | -             |  false   | Replication factor for `mgr-tenant-entitlements.scheduled-job` topic.                                                                                      |
| KAFKA_CAPABILITY_TOPIC_PARTITIONS            | 1             |  false   | Amount of partitions for `capabilities` topic.                                                                                                             |
| KAFKA_CAPABILITY_TOPIC_REPLICATION_FACTOR    | -             |  false   | Replication factor for `capabilities` topic.                                                                                                               |
| KAFKA_SYS_USER_TOPIC_PARTITIONS              | 1             |  false   | Amount of partitions for `system-user` topic.                                                                                                              |
| KAFKA_SYS_USER_TOPIC_REPLICATION_FACTOR      | -             |  false   | Replication factor for `system-user` topic.                                                                                                                |
| KAFKA_SEND_DURATION_TIMEOUT                  | 10s           |  false   | A default duration for KafkaEventPublisher will wait for the message acknowledgment from kafka                                                             |
| KAFKA_PRODUCER_TENANT_COLLECTION             | false         |  false   | Defines usage Kafka Topic Tenant Collection for tenant specific events                                                                                     |

### SSL Configuration environment variables

| Name                          | Default value | Required | Description                                                            |
|:------------------------------|:--------------|:--------:|:-----------------------------------------------------------------------|
| SERVER_PORT                   | 8081          |  false   | Server HTTP port. Should be specified manually in case of SSL enabled. |
| SERVER_SSL_ENABLED            | false         |  false   | Manage server's mode. If `true` then SSL will be enabled.              |
| SERVER_SSL_KEY_STORE          |               |  false   | Path to the keystore.  Mandatory if `SERVER_SSL_ENABLED` is `true`.    |
| SERVER_SSL_KEY_STORE_TYPE     | BCFKS         |  false   | Type of the keystore. By default `BCFKS` value is used.                |
| SERVER_SSL_KEY_STORE_PROVIDER | BCFIPS        |  false   | Provider of the keystore.                                              |
| SERVER_SSL_KEY_STORE_PASSWORD |               |  false   | Password for keystore.                                                 |
| SERVER_SSL_KEY_PASSWORD       |               |  false   | Password for key in keystore.                                          |

### Secure storage environment variables

#### AWS-SSM

Required when `SECRET_STORE_TYPE=AWS_SSM`

| Name                                          | Default value | Description                                                                                                                                                    |
|:----------------------------------------------|:--------------|:---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| SECRET_STORE_AWS_SSM_REGION                   | -             | The AWS region to pass to the AWS SSM Client Builder. If not set, the AWS Default Region Provider Chain is used to determine which region to use.              |
| SECRET_STORE_AWS_SSM_USE_IAM                  | true          | If true, will rely on the current IAM role for authorization instead of explicitly providing AWS credentials (access_key/secret_key)                           |
| SECRET_STORE_AWS_SSM_ECS_CREDENTIALS_ENDPOINT | -             | The HTTP endpoint to use for retrieving AWS credentials. This is ignored if useIAM is true                                                                     |
| SECRET_STORE_AWS_SSM_ECS_CREDENTIALS_PATH     | -             | The path component of the credentials endpoint URI. This value is appended to the credentials endpoint to form the URI from which credentials can be obtained. |

#### Vault

Required when `SECRET_STORE_TYPE=VAULT`

| Name                                    | Default value | Description                                                                         |
|:----------------------------------------|:--------------|:------------------------------------------------------------------------------------|
| SECRET_STORE_VAULT_TOKEN                | -             | token for accessing vault, may be a root token                                      |
| SECRET_STORE_VAULT_ADDRESS              | -             | the address of your vault                                                           |
| SECRET_STORE_VAULT_ENABLE_SSL           | false         | whether or not to use SSL                                                           |
| SECRET_STORE_VAULT_PEM_FILE_PATH        | -             | the path to an X.509 certificate in unencrypted PEM format, using UTF-8 encoding    |
| SECRET_STORE_VAULT_KEYSTORE_PASSWORD    | -             | the password used to access the JKS keystore (optional)                             |
| SECRET_STORE_VAULT_KEYSTORE_FILE_PATH   | -             | the path to a JKS keystore file containing a client cert and private key            |
| SECRET_STORE_VAULT_TRUSTSTORE_FILE_PATH | -             | the path to a JKS truststore file containing Vault server certs that can be trusted |

#### Folio Secure Store Proxy (FSSP)

Required when `SECRET_STORE_TYPE=FSSP`

| Name                                   | Default value         | Description                                          |
|:---------------------------------------|:----------------------|:-----------------------------------------------------|
| SECRET_STORE_FSSP_ADDRESS              | -                     | The address (URL) of the FSSP service.               |
| SECRET_STORE_FSSP_SECRET_PATH          | secure-store/entries  | The path in FSSP where secrets are stored/retrieved. |
| SECRET_STORE_FSSP_ENABLE_SSL           | false                 | Whether to use SSL when connecting to FSSP.          |
| SECRET_STORE_FSSP_TRUSTSTORE_PATH      | -                     | Path to the truststore file for SSL connections.     |
| SECRET_STORE_FSSP_TRUSTSTORE_FILE_TYPE | -                     | The type of the truststore file (e.g., JKS, PKCS12). |
| SECRET_STORE_FSSP_TRUSTSTORE_PASSWORD  | -                     | The password for the truststore file.                |

### Keycloak Integration

#### Import Keycloak data on startup

As startup, the application creates/updates necessary records in Keycloak from the internal module descriptor:

- Resource server
- Client - with credentials of `KC_CLIENT_ID`/`KC_CLIENT_SECRET`.
- Resources - mapped from descriptor routing entries.
- Permissions - mapped from `requiredPermissions` of routing entries.
- Roles - mapped from permission sets of descriptor.
- Policies - role policies as well as aggregate policies (specific for each resource).

#### Keycloak Security

Keycloak can be used as a security provider. If enabled - application will delegate endpoint permissions evaluation to
Keycloak.
A valid Keycloak JWT token must be passed for accessing secured resources.
The feature is controlled by two env variables `SECURITY_ENABLED` and `KC_INTEGRATION_ENABLED`.

#### Automatic Token Refresh for Long-Running Operations

During long-running entitlement operations (upgrades, installations), user-provided access tokens may expire before the
operation completes. To prevent failures, `mgr-tenant-entitlements` automatically refreshes tokens when communicating
with `mgr-tenants` and `mgr-applications` services.

**How it works:**
- When Keycloak integration is enabled, a `TokenRefreshRequestInterceptor` transparently replaces user-provided tokens
  with fresh system tokens obtained using Keycloak's client credentials flow
- Tokens are cached and reused until near expiration (controlled by `KC_AUTHORIZATION_CACHE_TTL_OFFSET`)
- When Keycloak is disabled, user tokens pass through unchanged

**Configuration:**
- Requires `KC_INTEGRATION_ENABLED=true`
- Uses the admin client configured via `KC_ADMIN_CLIENT_ID` and `KC_ADMIN_CLIENT_SECRET`
- Token cache behavior controlled by `KC_AUTHORIZATION_CACHE_MAX_SIZE` and `KC_AUTHORIZATION_CACHE_TTL_OFFSET`

### Keycloak specific environment variables

| Name                              | Default value              |  Required   | Description                                                                                                                                                      |
|:----------------------------------|:---------------------------|:-----------:|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| KC_URL                            | http://keycloak:8080       |    false    | Keycloak URL used to perform HTTP requests.                                                                                                                      |
| KC_INTEGRATION_ENABLED            | true                       |    false    | Defines if Keycloak integration is enabled or disabled.<br/>If it set to `false` - it will exclude all keycloak-related beans from spring context.               |
| KC_IMPORT_ENABLED                 | false                      |    false    | If true - at startup, register/create necessary records in keycloak from the internal module descriptor.                                                         |
| KC_ADMIN_CLIENT_ID                | folio-backend-admin-client |    false    | Keycloak client id. Used for register/create necessary records in keycloak from the internal module descriptor.                                                  |
| KC_ADMIN_CLIENT_SECRET            | -                          | conditional | Keycloak client secret. Required only if admin username/password are not set.                                                                                    |
| KC_ADMIN_USERNAME                 | -                          | conditional | Keycloak admin username. Required only if admin secret is not set.                                                                                               |
| KC_ADMIN_PASSWORD                 | -                          | conditional | Keycloak admin password. Required only if admin secret is not set.                                                                                               |
| KC_ADMIN_GRANT_TYPE               | client_credentials         |    false    | Keycloak admin grant type. Should be set to `password` if username/password are used instead of client secret.                                                   |
| KC_CLIENT_ID                      | mgr-tenant-entitlements    |    false    | client id to be imported to Keycloak.                                                                                                                            |
| KC_CLIENT_SECRET                  | -                          |    true     | client secret to be imported to Keycloak.                                                                                                                        |
| KC_LOGIN_CLIENT_SUFFIX            | -login-application         |    false    | Login Client name suffix for building full client name like {tenantName}{suffix} for creating client resources based on ModuleDescriptors during the entitlement |
| KC_CLIENT_TLS_ENABLED             | false                      |    false    | Enables TLS for keycloak clients.                                                                                                                                |
| KC_CLIENT_TLS_TRUSTSTORE_PATH     | -                          |    false    | Truststore file path for keycloak clients.                                                                                                                       |
| KC_CLIENT_TLS_TRUSTSTORE_PASSWORD | -                          |    false    | Truststore password for keycloak clients.                                                                                                                        |
| KC_CLIENT_TLS_TRUSTSTORE_TYPE     | -                          |    false    | Truststore file type for keycloak clients.                                                                                                                       |
| KC_AUTH_TOKEN_VALIDATE_URI        | false                      |    false    | Defines if validation for JWT must be run to compare configuration URL and token issuer for keycloak.                                                            |
| KC_JWKS_BASE_URL                  |                            |    false    | Custom base URL for JWKS endpoint. If specified, will be used instead of issuer URL from token's iss claim (e.g., http://keycloak:8080).                         |
| KC_JWKS_REFRESH_INTERVAL          | 60                         |    false    | Jwks refresh interval for realm JWT parser (in minutes).                                                                                                         |
| KC_FORCED_JWKS_REFRESH_INTERVAL   | 60                         |    false    | Forced jwks refresh interval for realm JWT parser (used in signing key rotation, in minutes).                                                                    |
| KC_AUTHORIZATION_CACHE_MAX_SIZE   | 50                         |    false    | Maximum amount of entries for keycloak authorization cache.                                                                                                      |
| KC_AUTHORIZATION_CACHE_TTL_OFFSET | 5000                       |    false    | TTL Offset for cached authorization information, positive, in millis.                                                                                            |

### Retry configuration

| Name                                | Default value |  Required   | Description                                       |
|:------------------------------------|:--------------|:-----------:|:--------------------------------------------------|
| RETRIES_MODULE_MAX                  | 3             |    false    | Maximum number of retries for FOLIO module calls  |
| RETRIES_KEYCLOAK_MAX                | 3             |    false    | Maximum number of retries for Keycloak calls      |
| RETRIES_MODULE_BACKOFF_DELAY        | 1000          |    false    | FOLIO module calls retries initial delay millisec |
| RETRIES_MODULE_BACKOFF_MAXDELAY     | 30000         |    false    | FOLIO module calls retries maximum delay millisec |
| RETRIES_MODULE_BACKOFF_MULTIPLIER   | 5             |    false    | FOLIO module calls retries delay multiplier       |
| RETRIES_KEYCLOAK_BACKOFF_DELAY      | 1000          |    false    | Keycloak calls retries initial delay millisec     |
| RETRIES_KEYCLOAK_BACKOFF_MAXDELAY   | 30000         |    false    | Keycloak calls retries maximum delay millisec     |
| RETRIES_KEYCLOAK_BACKOFF_MULTIPLIER | 5             |    false    | Keycloak calls retries delay multiplier           |

## Kafka Integration

### Events upon application being enabled/disabled

* The mte publishes lightweight kafka events when application is enabled/disabled.
* The topic is being created on application startup.

#### Naming convention

topic naming convention:`<prefix>_entitlement`

* Prefix is passed in as environment variable (for FSE it's usually the cluster identifier, e.g. "evrk")

#### Event structure

```json
{
  "applicationId": "application1-1.2.3"
}
```

## FAQ

### How ignoreErrors affects tenant entitlement?

_If `ignoreErrors` is set to `false` (which is default value) then the stage rollback operations will be executed
if one or more stages failed.
Stage rollbacks will return the system to initial state:_
- _All installed tenant modules will be uninstalled_
- _All created Kong Gateway routes will be deleted for the application_
- _All created Keycloak resources will be deleted for the application_

_If the `ignoreErrors` is set to `true` - stage rollbacks are disabled for tenant entitlement and if
one of the stages fail - entitlement process will stop, changes will stay in the system, and user can repeat
entitlement process until it succeeds._

_The stage fails caused by misconfiguration of the container: networking parameters, database pool size, etc.
The application checked before entitlement that all interfaces are compatible, and if not - validation error
will be raised while running the entitlement process._

### How purgeOnRollback affects tenant entitlement process

_This argument is only applied if tenant entitlement is executed with `ignoreErrors=false` and skips every rollback
operation for each stage, leading to the state when:_
- _Tenant routes are not affected_
- _Keycloak authorization resources and permissions are not affected_
- _Installed modules are not uninstalled_

_The behaviour is the same as calling `POST /entitlements` with `ignoreErrors=true`_

### How purge flag is working for application uninstalling?

_The `purge` flag defines whether the tenant application data will be deleted. If this flag is set to true -
`mgr-tenant-entitlement` will delete all resources, including routes, keycloak authorization resources, and module
database data (using tenant API) from the system. If this flag is set to `false` - sidecars for this tenant will
be disabled and module requests will return an error, saying that the tenant is not enabled. Also routes in Kong will
be deleted._

### Is rollbacks are supported for the application uninstalling/upgrades?

_No, rollbacks are not supported, because the folio modules don't support downgrades, and storing the data before
uninstalling or upgrading is complicated._

### How async flag works?

_The Async flag returns the flow identifier instantly, allowing the user to track the progress of installation,
uninstallation, or upgrading the application using the `/entitlement-flow/{id}` endpoint. If this flag is set
to `false` - `mgr-tenant-entitlement` holds the response until the entitlement flow is executed, however, the
flow execution implementation is the same as calling the entitlement process using `async=true`._
