package org.folio.entitlement.it;

import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.folio.entitlement.utils.WireMockUtil.stubAnyHttpMethod;
import static org.folio.entitlement.utils.WireMockUtil.stubDelete;
import static org.folio.entitlement.utils.WireMockUtil.stubGet;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.entitlement.support.extensions.EnableKongGateway;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.kong.client.KongAdminClient.KongResultList;
import org.folio.tools.kong.model.Route;
import org.folio.tools.kong.model.Service;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@EnableKeycloakTlsMode
@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
@TestPropertySource(properties = {
  "application.keycloak.enabled=false",
  "application.okapi.enabled=false",
  "application.kong.enabled=true",
  "application.clients.folio.connect-timeout=250ms",
  "application.clients.folio.read-timeout=250ms",
  "retries.module.backoff.delay=1",
  "retries.module.backoff.multiplier=1"
})
@EnableKongGateway(enableWiremock = true)
public class KongRetriesIT extends BaseIntegrationTest {

  private static final String FOLIO_APP1_ID = "folio-app1-1.0.0";
  private static final String FOLIO_APP2_ID = "folio-app2-2.0.0";

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {"/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json", "/wiremock/folio-module1/install.json"})
  void verifyModuleRegistrationRetry() throws Exception {
    var entitlementRequest = entitlementRequest(FOLIO_APP1_ID);
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    var request =
      post(updatePathWithPrefix("/entitlements")).contentType(APPLICATION_JSON).header(TOKEN, getSystemAccessToken())
        .content(asJsonString(entitlementRequest));
    queryParams.forEach(request::queryParam);

    var wireMockClient = getWireMockClient();
    stubAnyHttpMethod(wireMockClient, 1, urlMatching("/services/folio-module1-1.0.0"), null, 500);

    mockMvc.perform(request).andExpect(content().contentType(APPLICATION_JSON)).andReturn();

    var endpointsCalled = wireMockClient.getServeEvents().stream().filter(e -> e.getResponse().getStatus() == 500)
      .map(e -> e.getRequest().getUrl()).toList();
    assertEquals(3, endpointsCalled.size());
    endpointsCalled.forEach(endpoint -> assertEquals("/services/folio-module1-1.0.0", endpoint));
  }

  @Test
  @Sql("classpath:/sql/folio-entitlement.sql")
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {"/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app2/get-discovery.json", "/wiremock/folio-module2/uninstall.json"})
  void verifyModuleDeregistrationRetry() throws Exception {
    var moduleId = "folio-module2-2.0.0";
    var routeId = "mockroute1";
    var wireMockClient = getWireMockClient();
    var mockServiceInfo = new Service();
    mockServiceInfo.setId(moduleId);
    stubGet(wireMockClient, 1, urlMatching("/services/" + moduleId), mockServiceInfo);

    var routesResponseMock = new KongResultList<Route>();
    var route = new Route();
    route.setId(routeId);
    routesResponseMock.setData(List.of(route));
    stubGet(wireMockClient, 1, urlMatching("/routes\\?tags=test%2C" + moduleId + "(&offset=0)?"), routesResponseMock);

    stubDelete(wireMockClient, 1, urlMatching("/services/" + moduleId + "/routes/" + routeId), null, 500);

    var entitlementRequest = entitlementRequest(FOLIO_APP2_ID);
    var queryParams = Map.of("purge", "true", "ignoreErrors", "true");

    var request =
      delete(updatePathWithPrefix("/entitlements")).contentType(APPLICATION_JSON).header(TOKEN, getSystemAccessToken())
        .content(asJsonString(entitlementRequest));
    queryParams.forEach(request::queryParam);

    mockMvc.perform(request).andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].parameters[0].value").value(containsString("Failed to remove routes")));

    var endpointsCalled = wireMockClient.getServeEvents().stream().filter(e -> e.getResponse().getStatus() == 500)
      .map(e -> e.getRequest().getUrl()).toList();
    assertEquals(3, endpointsCalled.size());
    endpointsCalled.forEach(endpoint -> assertEquals("/services/" + moduleId + "/routes/" + routeId, endpoint));
  }
}
