package org.folio.entitlement.it;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.validator.InterfaceIntegrityValidator;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
@TestPropertySource(properties = {
  "application.keycloak.enabled=false",
  "application.okapi.enabled=false",
  "application.kong.enabled=false"
})
class EntitlementValidationIT extends BaseIntegrationTest {

  @Nested
  class InterfaceIntegrity {

    private static final String VALIDATOR = InterfaceIntegrityValidator.class.getSimpleName();

    private static final String FOLIO_APP_NAME_1 = "folio-app1";
    private static final String FOLIO_APP_ID_1 = "folio-app1-1.0.0";
    private static final String FOLIO_APP_ID_2 = "folio-app2-2.0.0";
    private static final String FOLIO_APP_ID_3 = "folio-app3-3.0.0";

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-applications/folio-app-mixed/get-by-ids-123-query-full.json"
    })
    void validate_positive() throws Exception {
      var request = entitlementRequest(TENANT_ID, FOLIO_APP_ID_1, FOLIO_APP_ID_2, FOLIO_APP_ID_3);

      attemptPost("/entitlements/validate?entitlementType={type}&validator={validator}",
        request, ENTITLE.getValue(), VALIDATOR)
        .andExpect(status().isNoContent());
    }

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-tenants/test/get.json",
      "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
      "/wiremock/mgr-applications/folio-app1/get-discovery.json",
      "/wiremock/mgr-applications/validate-any-descriptor.json",
      "/wiremock/folio-module1/install.json",
      "/wiremock/mgr-applications/folio-app-mixed/get-by-ids-23-query-full.json"
    })
    void validate_positive_parentEntitled() throws Exception {
      var parentRequest = entitlementRequest(FOLIO_APP_ID_1);
      var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
      entitleApplications(parentRequest, queryParams, extendedEntitlements(entitlement(FOLIO_APP_ID_1)));

      var request = entitlementRequest(TENANT_ID, FOLIO_APP_ID_2, FOLIO_APP_ID_3);

      attemptPost("/entitlements/validate?entitlementType={type}&validator={validator}",
        request, ENTITLE.getValue(), VALIDATOR)
        .andExpect(status().isNoContent());
    }

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-applications/folio-app-mixed/get-by-ids-23-query-full.json"
    })
    void validate_negative_entitledNotFound() throws Exception {
      var request = entitlementRequest(TENANT_ID, FOLIO_APP_ID_2, FOLIO_APP_ID_3);

      attemptPost("/entitlements/validate?entitlementType={type}&validator={validator}",
        request, ENTITLE.getValue(), VALIDATOR)
        .andExpectAll(requestValidationErr(
          "Entitled application(s) is not found", "applicationName(s)", FOLIO_APP_NAME_1));
    }

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-tenants/test/get.json",
      "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
      "/wiremock/mgr-applications/folio-app1/get-discovery.json",
      "/wiremock/mgr-applications/validate-any-descriptor.json",
      "/wiremock/folio-module1/install.json",
      "/wiremock/mgr-applications/folio-app-mixed/get-by-ids-23-query-dependency-ver-mismatch-full.json"
    })
    void validate_negative_dependencyVersionMismatch_with_entitledApp() throws Exception {
      var parentRequest = entitlementRequest(FOLIO_APP_ID_1);
      var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
      entitleApplications(parentRequest, queryParams, extendedEntitlements(entitlement(FOLIO_APP_ID_1)));

      var request = entitlementRequest(TENANT_ID, FOLIO_APP_ID_2, FOLIO_APP_ID_3);

      attemptPost("/entitlements/validate?entitlementType={type}&validator={validator}",
        request, ENTITLE.getValue(), VALIDATOR)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].message", containsString(
          "Version mismatch between installed application and dependency of application with id: " + FOLIO_APP_ID_2)))
        .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
        .andExpect(jsonPath("$.errors[0].type", is(RequestValidationException.class.getSimpleName())))
        .andExpect(jsonPath("$.errors[0].parameters[0].key", is("installedApplication")))
        .andExpect(jsonPath("$.errors[0].parameters[0].value", is("folio-app1 1.0.0")))
        .andExpect(jsonPath("$.errors[0].parameters[1].key", is("dependency")))
        .andExpect(jsonPath("$.errors[0].parameters[1].value", is("folio-app1 ~2.0")))
        .andExpect(jsonPath("$.total_records", is(1)));
    }

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-tenants/test/get.json",
      "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
      "/wiremock/mgr-applications/folio-app1/get-discovery.json",
      "/wiremock/mgr-applications/validate-any-descriptor.json",
      "/wiremock/folio-module1/install.json",
      "/wiremock/mgr-applications/folio-app-mixed/get-by-ids-23-query-missing-dependencies-full.json"
    })
    void validate_negative_missingInterfaces() throws Exception {
      var parentRequest = entitlementRequest(FOLIO_APP_ID_1);
      var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
      entitleApplications(parentRequest, queryParams, extendedEntitlements(entitlement(FOLIO_APP_ID_1)));

      var request = entitlementRequest(TENANT_ID, FOLIO_APP_ID_2, FOLIO_APP_ID_3);

      attemptPost("/entitlements/validate?entitlementType={type}&validator={validator}",
        request, ENTITLE.getValue(), VALIDATOR)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].message", containsString("Missing dependencies found for the applications")))
        .andExpect(jsonPath("$.errors[0].code", is("validation_error")))
        .andExpect(jsonPath("$.errors[0].type", is(RequestValidationException.class.getSimpleName())))
        .andExpect(jsonPath("$.errors[0].parameters[0].key", is(FOLIO_APP_ID_2)))
        .andExpect(jsonPath("$.errors[0].parameters[0].value", is("folio-moduleX-api 1.0")))
        .andExpect(jsonPath("$.errors[0].parameters[1].key", is(FOLIO_APP_ID_3)))
        .andExpect(jsonPath("$.errors[0].parameters[1].value", is("folio-moduleY-api 1.0")))
        .andExpect(jsonPath("$.total_records", is(1)));
    }
  }
}
