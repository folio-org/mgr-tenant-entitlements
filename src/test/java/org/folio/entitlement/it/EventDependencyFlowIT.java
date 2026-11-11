package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestValues.desiredStateRequest;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.folio.entitlement.service.validator.EventDependencyValidator;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Runs the real entitle, upgrade and desired-state flows for an application whose module consumes an event that
 * nobody produces, with strict event validation on and off.
 */
class EventDependencyFlowIT {

  private static final String VALIDATOR = EventDependencyValidator.class.getSimpleName();
  private static final String APP_ID = "folio-app1-1.0.0";
  private static final String APP_V1_1_ID = "folio-app1-1.1.0";
  private static final Map<String, String> QUERY_PARAMS =
    Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");

  @Nested
  @IntegrationTest
  @SqlMergeMode(MERGE)
  @Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
  @TestPropertySource(properties = {
    "application.keycloak.enabled=false",
    "application.apigw.enabled=false",
    "application.validation.event-dependency.strict-event-validation=false"
  })
  class StrictValidationDisabled extends BaseIntegrationTest {

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-tenants/test/get.json",
      "/wiremock/mgr-applications/event-dependency/get-by-ids-app1-consuming-missing-event.json",
      "/wiremock/mgr-applications/folio-app1/get-discovery.json",
      "/wiremock/mgr-applications/validate-any-descriptor.json",
      "/wiremock/folio-module1/install.json"
    })
    void entitle_positive_missingProducerOnlyWarns() throws Exception {
      var result = entitleApplications(entitlementRequest(APP_ID), QUERY_PARAMS,
        extendedEntitlements(entitlement(APP_ID)));

      assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.CREATED.value());
    }

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-tenants/test/get.json",
      "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
      "/wiremock/mgr-applications/folio-app1/get-discovery.json",
      "/wiremock/mgr-applications/event-dependency/get-by-ids-app1-v1.1-consuming-missing-event.json",
      "/wiremock/mgr-applications/folio-app1/get-discovery-v1.1.json",
      "/wiremock/mgr-applications/validate-any-descriptor.json",
      "/wiremock/folio-module1/install.json",
      "/wiremock/folio-module1/uninstall.json"
    })
    void upgrade_positive_missingProducerOnlyWarns() throws Exception {
      entitleApplications(entitlementRequest(APP_ID), QUERY_PARAMS, extendedEntitlements(entitlement(APP_ID)));

      var result = upgradeApplications(entitlementRequest(APP_V1_1_ID), QUERY_PARAMS,
        extendedEntitlements(entitlement(APP_V1_1_ID)));

      assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-tenants/test/get.json",
      "/wiremock/mgr-applications/event-dependency/get-by-ids-app1-consuming-missing-event.json",
      "/wiremock/mgr-applications/folio-app1/get-discovery.json",
      "/wiremock/mgr-applications/validate-any-descriptor.json",
      "/wiremock/folio-module1/install.json"
    })
    void desiredState_positive_missingProducerOnlyWarns() throws Exception {
      var result = stateOfApplications(desiredStateRequest(TENANT_ID, APP_ID), QUERY_PARAMS,
        extendedEntitlements(entitlement(APP_ID)));

      assertThat(result.getResponse().getStatus()).isEqualTo(HttpStatus.OK.value());
    }
  }

  @Nested
  @IntegrationTest
  @SqlMergeMode(MERGE)
  @Sql(scripts = "classpath:/sql/truncate-tables.sql", executionPhase = AFTER_TEST_METHOD)
  @TestPropertySource(properties = {
    "application.keycloak.enabled=false",
    "application.apigw.enabled=false",
    "application.validation.event-dependency.strict-event-validation=true"
  })
  class StrictValidationEnabled extends BaseIntegrationTest {

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-tenants/test/get.json",
      "/wiremock/mgr-applications/event-dependency/get-by-ids-app1-consuming-missing-event.json"
    })
    void entitle_negative_missingProducerFailsRequest() throws Exception {
      assertEventValidationFailure(mockMvc.perform(post(updatePathWithPrefix("/entitlements"))
        .contentType(APPLICATION_JSON)
        .header(TOKEN, getSystemAccessToken())
        .content(asJsonString(entitlementRequest(APP_ID)))), "FlowCancelledException");
    }

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-tenants/test/get.json",
      "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
      "/wiremock/mgr-applications/folio-app1/get-discovery.json",
      "/wiremock/mgr-applications/event-dependency/get-by-ids-app1-v1.1-consuming-missing-event.json",
      "/wiremock/mgr-applications/validate-any-descriptor.json",
      "/wiremock/folio-module1/install.json"
    })
    void upgrade_negative_missingProducerFailsRequest() throws Exception {
      entitleApplications(entitlementRequest(APP_ID), QUERY_PARAMS, extendedEntitlements(entitlement(APP_ID)));

      assertEventValidationFailure(mockMvc.perform(put(updatePathWithPrefix("/entitlements"))
        .contentType(APPLICATION_JSON)
        .header(TOKEN, getSystemAccessToken())
        .content(asJsonString(entitlementRequest(APP_V1_1_ID)))), "FlowExecutionException");
    }

    @Test
    @WireMockStub(scripts = {
      "/wiremock/mgr-tenants/test/get.json",
      "/wiremock/mgr-applications/event-dependency/get-by-ids-app1-consuming-missing-event.json"
    })
    void desiredState_negative_missingProducerFailsRequest() throws Exception {
      assertEventValidationFailure(mockMvc.perform(put(updatePathWithPrefix("/entitlements/state"))
        .contentType(APPLICATION_JSON)
        .header(TOKEN, getSystemAccessToken())
        .content(asJsonString(desiredStateRequest(TENANT_ID, APP_ID)))), "FlowCancelledException");
    }

    private static void assertEventValidationFailure(ResultActions result, String flowExceptionType)
      throws Exception {
      result.andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.errors[0].type", is(flowExceptionType)))
        .andExpect(jsonPath("$.errors[0].parameters[0].key", is(VALIDATOR)))
        .andExpect(jsonPath("$.errors[0].parameters[0].value",
          containsString("Consumed events without producers found: evt.missing")));
    }
  }
}
