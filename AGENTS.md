# mgr-tenant-entitlements - Copilot Instructions

This is a FOLIO module that manages tenant entitlements, handling application installation, upgrades, and dependency management using a flow-based execution engine.

## Build, Test, and Lint Commands

### Building
```bash
# Full build with tests
mvn clean install

# Skip tests
mvn clean install -DskipTests

# Build with coverage
mvn clean install -Pcoverage
```

### Testing
Tests are split between repo-local unit tests and an integration suite centered on `BaseIntegrationTest`:

```bash
# Run unit tests only (tagged with @UnitTest)
mvn test

# Run the full verification lifecycle (unit + integration suites)
mvn verify

# Run both
mvn clean install

# Run a single test class
mvn test -Dtest=EntitlementServiceTest

# Run a single test method
mvn test -Dtest=EntitlementServiceTest#shouldReturnEntitlements

# Run with coverage report
mvn clean install -Pcoverage
# Coverage report: target/site/jacoco-aggregate/index.html
```

**Test Requirements:**
- Unit tests use `@UnitTest`
- Integration tests are centered on `BaseIntegrationTest` and use `@IntegrationTest`
- Test coverage spans `configuration`, `controller`, `integration`, `it`, `service`, `support`, and `utils`, with nested coverage under `service/stage`, `service/flow`, and `service/validator`

### Linting
```bash
# Run checkstyle (runs automatically during build)
mvn checkstyle:check

# Generate checkstyle report
mvn checkstyle:checkstyle
```

**Checkstyle Rules:**
- Configuration: `checkstyle/checkstyle-checker.properties`
- Suppressions: `checkstyle/checkstyle-suppressions.xml`
- Base config: `folio-checkstyle/checkstyle.xml` (from folio-java-checkstyle dependency)
- Max method length: 23 lines

### Docker
```bash
# Build Docker image
docker build -t mgr-tenant-entitlements .

# Run Docker container
docker run \
  --name mgr-tenant-entitlements \
  -e DB_HOST=postgres \
  -e okapi.url=http://okapi:9130 \
  -e tenant.url=http://mgr-tenants:8081 \
  -e am.url=http://mgr-applications:8081 \
  -p 8081:8081 \
  -d mgr-tenant-entitlements
```

## Architecture

### High-Level Flow Engine Architecture

The application is built around the **Flow Engine** (`folio-flow-engine` library), which orchestrates multi-stage entitlement operations:

1. **Flow Types** (in `service/flow/`):
   - **Entitle Flow**: Install new applications with dependencies
   - **Upgrade Flow**: Upgrade applications to new versions
   - **Revoke Flow**: Uninstall applications
   - **Desired State Flow**: Reconcile actual state with desired state

2. **Stage-Based Execution** (in `service/stage/`):
   - Flows are composed of sequential stages (e.g., `ApplicationDescriptorLoader`, `TenantLoader`, `DatabaseLoggingStage`)
   - Each stage is a discrete unit of work that can succeed, fail, or be retried
   - Stages can be parallelized (e.g., module installation via `FLOW_ENGINE_MODULE_INSTALLER_THREADS`)
   - Finalizers run at the end of flows regardless of success/failure

3. **Flow Lifecycle**:
   ```
   FlowInitializer → Stage1 → Stage2 → ... → StageN → FlowFinalizer
                                                    ↓ (on failure)
                                              FailedFlowFinalizer
   ```

4. **Application Dependency Management**:
   - Builds dependency graphs (`ApplicationInstallationGraph`, `ModuleInstallationGraph`)
   - Validates interface integrity across applications
   - Handles optional vs. required dependencies
   - Manages cross-application module dependencies

5. **Integration Points** (in `integration/`):
   - Active subpackages include `am/`, `tm/`, `okapi/`, `folio/`, `keycloak/`, `kafka/`, `token/`, and `interceptor/`
   - These packages cover application and tenant clients, Okapi/FOLIO interactions, auth/token handling, Kafka publishing, and request interception

### Key Domain Concepts

- **Entitlement**: A tenant's right to use a specific application and its modules
- **Application**: A collection of modules and dependencies (from Application Manager)
- **Module**: The atomic unit of deployment (FOLIO modules)
- **Flow Execution**: A tracked execution of a flow with stages, stored in database
- **Stage Context**: Thread-local context for sharing data between stages

## Key Conventions

### Code Organization

- **Package Structure**:
  - `controller/`: REST API controllers (implement OpenAPI-generated interfaces)
  - `service/`: Business logic
    - `service/flow/`: Flow providers and factories
    - `service/stage/`: Individual flow stages
    - `service/validator/`: Interface integrity and request validators
  - `integration/`: External clients and supporting runtime adapters
    - Active subpackages: `am/`, `tm/`, `okapi/`, `folio/`, `keycloak/`, `kafka/`, `token/`, `interceptor/`
  - `repository/`: JPA repositories
  - `domain/`: Domain models
    - `domain/entity/`: JPA entities
    - `domain/dto/`: OpenAPI-generated DTOs
    - `domain/model/`: Internal domain models
  - `mapper/`: MapStruct mappers

### Code Generation

**OpenAPI Generator:**
- Spec location: `src/main/resources/swagger.api/mgr-tenant-entitlements.yaml`
- Generated code: `target/generated-sources/`
- Generated packages:
  - API interfaces: `org.folio.entitlement.rest.resource`
  - DTOs: `org.folio.entitlement.domain.dto`
- **Convention**: Controllers implement generated API interfaces but add `@RestController` and `@RequestMapping` annotations

**MapStruct:**
- All mappers are interfaces annotated with `@Mapper(componentModel = "spring", injectionStrategy = CONSTRUCTOR)`
- Located in `mapper/` package
- Processors run during compilation via Maven compiler plugin

### Lombok Configuration

From `lombok.config`:
- `@Generated` annotation added automatically
- Constructor properties enabled
- Copies `@Qualifier` and `@Lazy` annotations to generated code

### Testing Conventions

- **Test class naming**:
  - Unit tests: `*Test.java` (tagged with `@UnitTest`)
  - Integration tests: `*IT.java` (centered on `BaseIntegrationTest` and using `@IntegrationTest`)
- **Base test classes**:
  - `BaseIntegrationTest` in `support/base/` anchors the integration suite
  - Common test utilities live in `support/`
- **Test layout**:
  - Test coverage spans `configuration`, `controller`, `integration`, `it`, `service`, `support`, and `utils`, with nested coverage under `service/stage`, `service/flow`, and `service/validator`
- **Mocking**: Use Mockito for unit tests
- **Integration tests**: Use Testcontainers, WireMock for external services

### Database

- **Schema management**: Liquibase (changelogs in `src/main/resources/changelog/`)
- **Versioning**: Changelogs are versioned with mixed directory naming styles (e.g., `changes/v1.0.0/`, `changes.v3.0.0/`, `changes.v4.0.0/`)
- **Main changelog**: `changelog/changelog-master.xml`

### Dependency Injection

- Constructor injection (enabled via Lombok `@RequiredArgsConstructor`)
- Spring Boot `4.0.6` with Java `21`
- App bootstrap uses `@EnableKafkaProducer`, `@EnableMgrSecurity`, `@ConfigurationPropertiesScan`, and imports `JpaCqlConfiguration` + `TransactionHelper`

### Security

- Keycloak integration via `@EnableMgrSecurity` (from `folio-security`)
- JWT token validation
- Token caching (Caffeine cache) for long-running operations
- Automatic token refresh for operations that exceed token TTL

### Environment Variables

Critical environment variables (see README for full list):
- `OKAPI_URL` / `okapi.url`: Okapi gateway URL
- `MT_CLIENT_URL` / `tenant.url`: Tenant Manager URL
- `AM_CLIENT_URL` / `am.url`: Application Manager URL
- `KONG_ADMIN_URL` / `kong.url`: Kong Admin API URL
- `KONG_INTEGRATION_ENABLED`: Enables Kong integration flows
- `OKAPI_INTEGRATION_ENABLED`: Enables Okapi integration flows
- `KC_INTEGRATION_ENABLED`: Enables Keycloak integration
- `SECRET_STORE_TYPE`: Selects the secure storage backend used by the service
- `FLOW_ENGINE_EXECUTION_TIMEOUT`: Flow execution timeout
- `FLOW_ENGINE_LAST_EXECUTIONS_CACHE_SIZE`: Recent flow cache size
- `FLOW_ENGINE_THREADS_NUM`: Flow engine thread pool size (default: 4)
- `FLOW_ENGINE_MODULE_INSTALLER_THREADS`: Parallel module installation threads (default: 4)
- `VALIDATION_INTERFACE_INTEGRITY_ENTITLEMENT_ENABLED`: Enables interface integrity validation for entitlement
- `VALIDATION_INTERFACE_INTEGRITY_UPGRADE_ENABLED`: Enables interface integrity validation for upgrade
- `VALIDATION_INTERFACE_COLLECTOR_MODE`: Interface collection mode (`combined` or `scoped`) for integrity checks
- `VALIDATION_INTERFACE_COLLECTOR_EXCLUDE_ENTITLED_REQUIRED`: Excludes required interfaces of already entitled apps from collection
- `VALIDATION_INTERFACE_INTEGRITY_STATE_ENABLED`: Enables interface integrity validation state checks
- `ENV`: Logical deployment name (kafka topic prefix)

### API Design

- OpenAPI 3.0 spec driven
- Async operations supported via `async` query parameter
- Pagination via standard query params (`offset`, `limit`)
- CQL (Contextual Query Language) for complex queries via `folio-spring-cql`

### Sonar Exclusions

From `pom.xml`, these are excluded from code coverage:
- `domain/**/*` - DTOs and entities (mostly Lombok-generated)
- `cql/**/*` - CQL classes
- `mapper/**/*` - MapStruct mappers
- `integration/**/model/**/*` - Integration model classes
- `rest/resource/**/*` - Generated API interfaces
- `TenantEntitlementApplication.java` - Main class

### Retry Logic

- Configurable retries for external calls (Keycloak, FOLIO modules)
- Retry configuration in `retry/` package
- Stage-level retry support in flow engine

### Common Pitfalls

1. **Generated code**: Don't modify generated DTOs or API interfaces in `target/generated-sources/`
2. **Test setup**: Keep unit tests on `@UnitTest`; keep integration coverage aligned with `BaseIntegrationTest` and `@IntegrationTest`
3. **Flow stages**: Stages must be idempotent where possible (may be retried)
4. **Thread safety**: Be aware of ThreadLocal usage in `ThreadLocalModuleStageContext`
5. **Kafka topics**: Topic names are prefixed with `ENV` variable
6. **Long operations**: Token refresh is automatic but ensure operations are cancellable

## Additional Resources

- Main README: `README.md` (comprehensive environment variable documentation)
- Release notes: `NEWS.md`
- OpenAPI spec: `src/main/resources/swagger.api/mgr-tenant-entitlements.yaml`
- CI pipeline template: `Jenkinsfile-disabled` (uses `folio_jenkins_shared_libs` with Java 21 agent)
