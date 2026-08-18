package org.folio.entitlement.it;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.controller.ApiExceptionHandler.FLOW_ID_HEADER;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestValues.desiredStateRequest;
import static org.folio.entitlement.support.TestValues.emptyEntitlements;
import static org.folio.entitlement.support.TestValues.queryByTenantAndAppId;
import static org.folio.test.TestConstants.OKAPI_AUTH_TOKEN;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.awaitility.Awaitility;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Verifies MGRENTITLE-161: a synchronous desired state request that outlives the flow execution timeout must end up
 * in a terminal state instead of blocking the tenant with a flow stuck in progress.
 *
 * <p>The module installation is slower than the flow execution timeout, but much faster than the folio client read
 * timeout, so the module call is still in flight when waiting for the flow result times out.</p>
 */
@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
@TestPropertySource(properties = {
  "application.apigw.enabled=false",
  "application.keycloak.enabled=false",
  "application.flow-engine.execution-timeout=1s",
  "application.clients.folio.connect-timeout=60s",
  "application.clients.folio.read-timeout=60s"
})
class FlowExecutionTimeoutIT extends BaseIntegrationTest {

  private static final String FOLIO_APP1_ID = "folio-app1-1.0.0";
  private static final String FOLIO_APP3_ID = "folio-app3-3.0.0";
  private static final String MODULE1_INSTALLER_STAGE_STATUS =
    "$.applicationFlows[0].stages[?(@.name == 'folio-module1-1.0.0-folioModuleInstaller')].status";

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install-slow.json"
  })
  void desiredState_negative_executionTimeout() throws Exception {
    var request = put("/entitlements/state")
      .contentType(APPLICATION_JSON)
      .header(TOKEN, OKAPI_AUTH_TOKEN)
      .content(asJsonString(desiredStateRequest(TENANT_ID, FOLIO_APP1_ID)))
      .queryParam("tenantParameters", "loadReference=true")
      .queryParam("ignoreErrors", "true");

    var mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andExpect(header().exists(FLOW_ID_HEADER))
      .andExpect(jsonPath("$.errors[0].type", is("FlowExecutionTimeoutException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[*].key", hasItem("timeout")))
      .andReturn();

    var flowId = mvcResult.getResponse().getHeader(FLOW_ID_HEADER);
    assertFlowIsFailed(flowId, 1);

    // the ~1s timeout fails the in-flight module installer stage row; its only possible later write is the terminal
    // outcome after the ~5s install returns, so it may read failed (or finished under CI load), never in_progress
    getFlowWithStages(flowId)
      .andExpect(jsonPath(MODULE1_INSTALLER_STAGE_STATUS, contains(oneOf("failed", "finished"))));

    // the flow engine cannot abort a running flow, so it completes in the background - the root FinishedFlowFinalizer
    // stage row is the completion beacon, while the already reported flow statuses must stay failed
    Awaitility.await().atMost(30, SECONDS).untilAsserted(() -> getFlowWithStages(flowId)
      .andExpect(jsonPath("$.status", is("failed")))
      .andExpect(jsonPath("$.applicationFlows", hasSize(1)))
      .andExpect(jsonPath("$.applicationFlows[*].status", everyItem(is("failed"))))
      .andExpect(jsonPath("$.stages[?(@.name == 'FinishedFlowFinalizer')].status", contains("finished"))));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP1_ID), emptyEntitlements());
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app-mixed/get-by-ids-13-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/folio-app3/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install-slow.json",
    "/wiremock/folio-module3/install.json"
  })
  void desiredState_negative_executionTimeout_queuedApplicationIsNotExecuted() throws Exception {
    var request = put("/entitlements/state")
      .contentType(APPLICATION_JSON)
      .header(TOKEN, OKAPI_AUTH_TOKEN)
      .content(asJsonString(desiredStateRequest(TENANT_ID, FOLIO_APP1_ID, FOLIO_APP3_ID)))
      .queryParam("tenantParameters", "loadReference=true")
      .queryParam("ignoreErrors", "true");

    var mvcResult = mockMvc.perform(request)
      .andExpect(status().isBadRequest())
      .andExpect(header().exists(FLOW_ID_HEADER))
      .andExpect(jsonPath("$.errors[0].type", is("FlowExecutionTimeoutException")))
      .andExpect(jsonPath("$.errors[0].parameters[*].key", hasItem("timeout")))
      .andReturn();

    var flowId = mvcResult.getResponse().getHeader(FLOW_ID_HEADER);
    assertFlowIsFailed(flowId, 2);

    // folio-app3 depends on folio-app1, so its application flow is queued behind the slow folio-app1 install; after
    // the timeout its initializer must refuse to run it, and the background flow ends with the root
    // FailedFlowFinalizer stage row
    Awaitility.await().atMost(30, SECONDS).untilAsserted(() -> getFlowWithStages(flowId)
      .andExpect(jsonPath("$.status", is("failed")))
      .andExpect(jsonPath("$.applicationFlows", hasSize(2)))
      .andExpect(jsonPath("$.applicationFlows[*].status", everyItem(is("failed"))))
      .andExpect(jsonPath("$.stages[?(@.name == 'FailedFlowFinalizer')].status", contains("finished"))));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP1_ID), emptyEntitlements());
    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP3_ID), emptyEntitlements());

    var folioModule3Calls = getWireMockClient().getServeEvents().stream()
      .map(serveEvent -> serveEvent.getRequest().getUrl())
      .filter(url -> url.startsWith("/folio-module3/"))
      .toList();
    assertThat(folioModule3Calls).isEmpty();
  }

  private static void assertFlowIsFailed(String flowId, int applicationFlowCount) throws Exception {
    mockMvc.perform(get("/entitlement-flows/{flowId}", flowId)
        .header(TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("failed")))
      .andExpect(jsonPath("$.applicationFlows", hasSize(applicationFlowCount)))
      .andExpect(jsonPath("$.applicationFlows[*].status", everyItem(is("failed"))));
  }

  private static ResultActions getFlowWithStages(String flowId) throws Exception {
    return mockMvc.perform(get("/entitlement-flows/{flowId}", flowId)
        .queryParam("includeStages", "true")
        .header(TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk());
  }
}
