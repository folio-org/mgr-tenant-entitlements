package org.folio.entitlement.it;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.entitlement.controller.ApiExceptionHandler.FLOW_ID_HEADER;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestValues.desiredStateRequest;
import static org.folio.test.TestConstants.OKAPI_AUTH_TOKEN;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
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
  "application.kong.enabled=false",
  "application.keycloak.enabled=false",
  "application.okapi.enabled=false",
  "application.flow-engine.execution-timeout=1s",
  "application.clients.folio.connect-timeout=60s",
  "application.clients.folio.read-timeout=60s"
})
class FlowExecutionTimeoutIT extends BaseIntegrationTest {

  private static final String FOLIO_APP1_ID = "folio-app1-1.0.0";

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
    assertFlowIsFailed(flowId);

    // the flow engine cannot abort a running flow, so it finishes in the background - its finalizer stage must not
    // replace the already reported status
    Awaitility.await().pollDelay(10, SECONDS).atMost(30, SECONDS)
      .untilAsserted(() -> assertFlowIsFailed(flowId));
  }

  private static void assertFlowIsFailed(String flowId) throws Exception {
    mockMvc.perform(get("/entitlement-flows/{flowId}", flowId)
        .header(TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.status", is("failed")))
      .andExpect(jsonPath("$.applicationFlows", hasSize(1)))
      .andExpect(jsonPath("$.applicationFlows[*].status", everyItem(is("failed"))));
  }
}
