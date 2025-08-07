package org.folio.entitlement.it;

import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.folio.entitlement.service.validator.InterfaceIntegrityValidator;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

class UpgradeValidationInterfaceIntegrityIT {

  private static final String VALIDATOR = InterfaceIntegrityValidator.class.getSimpleName();

  private static final String FOLIO_APP_NAME_1 = "folio-app1";
  private static final String FOLIO_APP1_ID = "folio-app1-1.0.0";
  private static final String FOLIO_APP2_V1_ID = "folio-app2-2.0.0";
  private static final String FOLIO_APP2_V2_ID = "folio-app2-3.0.0";
  private static final String FOLIO_APP3_ID = "folio-app3-3.0.0";
  private static final String FOLIO_APP7_ID = "folio-app7-7.0.0";

  @Nested
  @IntegrationTest
  @SqlMergeMode(MERGE)
  @Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
  @TestPropertySource(properties = {
    "application.keycloak.enabled=false",
    "application.okapi.enabled=false",
    "application.kong.enabled=false",
  })
  class ValidationEnabled extends BaseIntegrationTest {

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-tenants/test/get.json",
      "/wiremock/mgr-applications/folio-app-mixed/get-by-ids-12-query-full.json",
      "/wiremock/mgr-applications/folio-app1/get-discovery.json",
      "/wiremock/mgr-applications/folio-app2/get-discovery.json",
      "/wiremock/mgr-applications/validate-any-descriptor.json",
      "/wiremock/folio-module1/install.json",
      "/wiremock/folio-module2/install.json",
      "/wiremock/mgr-applications/folio-app-mixed/get-by-ids-1+2v2+3+7-query-full.json"
    })
    void validate_positive_correctDependencies() throws Exception {
      var parentRequest = entitlementRequest(TENANT_ID, FOLIO_APP1_ID, FOLIO_APP2_V1_ID);
      var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
      entitleApplications(parentRequest, queryParams,
        extendedEntitlements(entitlement(FOLIO_APP1_ID), entitlement(FOLIO_APP2_V1_ID)));

      var request = entitlementRequest(TENANT_ID, FOLIO_APP2_V2_ID, FOLIO_APP3_ID, FOLIO_APP7_ID);

      attemptPost("/entitlements/validate?entitlementType={type}&validator={validator}",
        request, UPGRADE.getValue(), VALIDATOR)
        .andExpect(status().isNoContent());
    }

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-tenants/test/get.json",
      "/wiremock/mgr-applications/folio-app-mixed/get-by-ids-12-query-full.json",
      "/wiremock/mgr-applications/folio-app1/get-discovery.json",
      "/wiremock/mgr-applications/folio-app2/get-discovery.json",
      "/wiremock/mgr-applications/validate-any-descriptor.json",
      "/wiremock/folio-module1/install.json",
      "/wiremock/folio-module2/install.json",
      "/wiremock/mgr-applications/folio-app-mixed/get-by-ids-1+2v2+3+7-query-missing-dependencies-full.json"
    })
    void validate_negative_missingDependencyInApp3App7() throws Exception {
      var parentRequest = entitlementRequest(TENANT_ID, FOLIO_APP1_ID, FOLIO_APP2_V1_ID);
      var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
      entitleApplications(parentRequest, queryParams,
        extendedEntitlements(entitlement(FOLIO_APP1_ID), entitlement(FOLIO_APP2_V1_ID)));

      var request = entitlementRequest(TENANT_ID, FOLIO_APP2_V2_ID, FOLIO_APP3_ID, FOLIO_APP7_ID);

      attemptPost("/entitlements/validate?entitlementType={type}&validator={validator}",
        request, UPGRADE.getValue(), VALIDATOR)
        .andExpect(status().isNoContent());
    }
  }
}
