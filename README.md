# mgr-tenant-entitlements

Copyright (C) 2022-2022 The Open Library Foundation

This software is distributed under the terms of the Apache License,
Version 2.0. See the file "[LICENSE](LICENSE)" for more information.

## Table of contents

* [Introduction](#introduction)
* [Compiling](#compiling)
* [Running It](#running-it)
* [Environment Variables](#environment-variables)

## Introduction

`mgr-tenant-entitlements` provides following functionality:

* Dependency check / platform integrity validation
* Enabling/disabling of an application (including dependencies)
* Optional integration with Kong gateway
  * Add/remove routes per tenant on application install/uninstall

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
| SECURITY_ENABLED                       | false                               |  false   | Allows to enable/disable security. If true and KEYCLOAK_INTEGRATION_ENABLED is also true - the Keycloak will be used as a security provider.                                                               |
| MT_TLS_ENABLED                         | false                               |  false   | Allows to enable/disable TLS connection to mgr-tenants module.                                                                                                                                             |
| MT_TLS_TRUSTSTORE_PATH                 | -                                   |  false   | Truststore file path for TLS connection to mgr-tenants module.                                                                                                                                             |
| MT_TLS_TRUSTSTORE_PASSWORD             | -                                   |  false   | Truststore password for TLS connection to mgr-tenants module.                                                                                                                                              |
| MT_TLS_TRUSTSTORE_TYPE                 | -                                   |  false   | Truststore file type for TLS connection to mgr-tenants module.                                                                                                                                             |
| FOLIO_CLIENT_CONNECT_TIMEOUT           | 10s                                 |  false   | Defines the connection timeout for the Java Http client, used to perform request to Folio Modules.                                                                                                         |
| FOLIO_CLIENT_READ_TIMEOUT              | 30s                                 |  false   | Defines the read timeout for the Java Http client, used to perform request to Folio Modules.                                                                                                               |
| MOD_AUTHTOKEN_URL                      | -                                   |   true   | Mod-authtoken URL. Required if OKAPI_INTEGRATION_ENABLED is true and SECURITY_ENABLED  is true and KC_INTEGRATION_ENABLED is false.                                                                        |
| SECRET_STORE_TYPE                      | -                                   |   true   | Secure storage type. Supported values: `EPHEMERAL`, `AWS_SSM`, `VAULT`                                                                                                                                     |
| MAX_HTTP_REQUEST_HEADER_SIZE           | 200KB                               |  false   | Maximum size of the HTTP request header.                                                                                                                                                                   |
| FLOW_ENGINE_EXECUTION_TIMEOUT          | 30m                                 |  false   | Maximum execution timeout for entitlement execution in sync mode.                                                                                                                                          |
| FLOW_ENGINE_LAST_EXECUTIONS_CACHE_SIZE | 25                                  |  false   | Max cache size for the latest flow executions                                                                                                                                                              |
| FLOW_ENGINE_PRINT_FLOW_RESULTS         | false                               |  false   | Defines if flow engine should print execution results in logs or not                                                                                                                                       |
| FLOW_ENGINE_THREADS_NUM                | 4                                   |  false   | Defines the number of threads for Fork-Join Pool used by flow engine.                                                                                                                                      |
| REGISTER_MODULE_IN_KONG                | true                                |  false   | Defines if module must be registered in Kong (it will create for itself service and list of routes from module descriptor)                                                                                 |
| ROUTER_PATH_PREFIX                     |                                     |  false   | Defines routes prefix to be added to the generated endpoints by OpenAPI generator (`/foo/entites` -> `{{prefix}}/foo/entities`). Required if load balancing group has format like `{{host}}/{{moduleId}}`  |

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
The feature is controlled by two env variables `SECURITY_ENABLED` and `KEYCLOAK_INTEGRATION_ENABLED`.

### Keycloak specific environment variables

| Name                              | Default value              |  Required   | Description                                                                                                                                                      |
|:----------------------------------|:---------------------------|:-----------:|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------|
| KC_URL                            | http://keycloak:8080       |    false    | Keycloak URL used to perform HTTP requests.                                                                                                                      |
| KC_INTEGRATION_ENABLED            | false                      |    false    | Defines if Keycloak integration is enabled or disabled.<br/>If it set to `false` - it will exclude all keycloak-related beans from spring context.               |
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

## Kong Gateway Integration

Kong gateway integration implemented using idempotent approach
with [Kong Admin API](https://docs.konghq.com/gateway/latest/admin-api/).

### Kong Service Registration

The Kong Gateway services are added on service discovery registration per application. Each Kong Service has tag equal
to `applicationId` to improve observability. Tags can be used to filter core entities, via the `?tags` querystring
parameter.

### Kong Route Registration

The Kong routes registered per-tenant using header filter:

```json
{
  "headers": {
    "x-okapi-tenant": [ "$tenantId" ]
  }
}
```

Routes as well populated with tags: `moduleId` and `tenantId` to be filtered.

Routes per tenant can be found with:

```shell
curl -XGET "$KONG_ADMIN_URL/routes?tags=$moduleId,$tenantId"
```

or

```shell
curl -XGET "$KONG_ADMIN_URL/services/$moduleId/routes?tags=$tenantId"
```

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
