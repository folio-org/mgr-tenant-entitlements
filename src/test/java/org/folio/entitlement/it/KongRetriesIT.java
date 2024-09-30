package org.folio.entitlement.it;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.entitlement.support.extensions.EnableKongGateway;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.EnableOkapiSecurity;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.kong.client.KongAdminClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {"/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json", "/wiremock/folio-module1/install.json"})
  void verifyModuleRegistration() throws Exception {
    var entitlementRequest = entitlementRequest(FOLIO_APP1_ID);
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    var request =
      post(updatePathWithPrefix("/entitlements")).contentType(APPLICATION_JSON).header(TOKEN, getSystemAccessToken())
        .content(asJsonString(entitlementRequest));
    queryParams.forEach(request::queryParam);

    var wireMockClient =
      new WireMock(new URI(wmAdminClient.getWireMockUrl()).getHost(), wmAdminClient.getWireMockPort());
    wireMockClient.register(
      any(urlMatching("(/services|/routes).*")).atPriority(1)
        .willReturn(aResponse().withStatus(500)));

    mockMvc.perform(request).andExpect(content().contentType(APPLICATION_JSON)).andReturn();

    List<String> endpointsCalled =
      wireMockClient.getServeEvents().stream().filter(e -> e.getResponse().getStatus() == 500)
        .map(e -> e.getRequest().getUrl()).toList();
    assertEquals(3, endpointsCalled.size());
    endpointsCalled.forEach(endpoint -> assertEquals("/services/folio-module1-1.0.0", endpoint));
  }
}
