package org.folio.entitlement.it;

import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.folio.entitlement.utils.WireMockUtil.stubAnyHttpMethod;
import static org.folio.entitlement.utils.WireMockUtil.stubGet;
import static org.folio.entitlement.utils.WireMockUtil.stubPost;
import static org.folio.security.integration.keycloak.utils.ClientBuildUtils.buildKeycloakAdminClient;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.github.tomakehurst.wiremock.client.WireMock;
import java.util.List;
import java.util.Map;
import org.folio.entitlement.integration.keycloak.KeycloakService;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties.Login;
import org.folio.entitlement.retry.keycloak.KeycloakRetrySupportService;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.security.integration.keycloak.configuration.properties.KeycloakAdminProperties;
import org.folio.security.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.security.integration.keycloak.service.KeycloakModuleDescriptorMapper;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.kong.client.KongAdminClient.KongResultList;
import org.folio.tools.kong.model.Route;
import org.folio.tools.kong.model.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.authorization.ScopeRepresentation;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
@TestPropertySource(properties = {
  "application.keycloak.enabled=true",
  "application.okapi.enabled=false",
  "application.kong.enabled=false",
  "application.clients.folio.connect-timeout=250ms",
  "application.clients.folio.read-timeout=250ms",
  "retries.module.backoff.delay=1",
  "retries.module.backoff.multiplier=1",
  "application.keycloak.admin.clientId=mgr-component-app"
})
@Import(KeycloakRetriesIT.TestConfiguration.class)
class KeycloakRetriesIT extends BaseIntegrationTest {

  private static final String FOLIO_APP1_ID = "folio-app1-1.0.0";
  private static final String FOLIO_APP2_ID = "folio-app2-2.0.0";

  @BeforeEach
  public void initKeycloakMock() throws Exception {
    var tokenResponse = new AccessTokenResponse();
    tokenResponse.setToken("mock_token");
    tokenResponse.setExpiresIn(6000000);
    stubPost(getWireMockClient(), 1, urlEqualTo("/realms/master/protocol/openid-connect/token"), tokenResponse, 200);
  }

  @Test
  @WireMockStub(scripts = {"/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json", "/wiremock/folio-module1/install.json"})
  void verifyModuleRegistrationRetry() throws Exception {
    var moduleId = "folio-module1-1.0.0";
    var wireMockClient = mockBaseDependencies(moduleId, "route123");

    var entitlementRequest = entitlementRequest(FOLIO_APP1_ID);
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    var request =
      post(updatePathWithPrefix("/entitlements")).contentType(APPLICATION_JSON).header(TOKEN, getSystemAccessToken())
        .content(asJsonString(entitlementRequest));
    queryParams.forEach(request::queryParam);

    stubPost(wireMockClient, 1, urlEqualTo("/admin/realms/test/clients/test/authz/resource-server/scope"), null, 201);
    stubAnyHttpMethod(wireMockClient, 1,
      urlMatching("/admin/realms/test/clients/test/authz/resource-server/resource.*"), null, 500);

    mockMvc.perform(request).andExpect(status().isBadRequest()).andExpect(
      jsonPath("$.errors[0].parameters[0].value").value(
        containsString("Failed to update authorization resources in Keycloak")));

    var endpointsCalled =
      wireMockClient.getServeEvents().stream().filter(e -> e.getResponse().getStatus() == 500).toList();
    assertThat(endpointsCalled.stream().filter(
      e -> e.getRequest().getUrl().equals("/admin/realms/test/clients/test/authz/resource-server/resource")
        && e.getRequest().getBodyAsString().contains("GET#folio-module1.events.item.get")).count()).isEqualTo(3);
    assertThat(endpointsCalled.stream().filter(
      e -> e.getRequest().getUrl().equals("/admin/realms/test/clients/test/authz/resource-server/resource")
        && e.getRequest().getBodyAsString().contains("POST#folio-module1.events.item.post")).count()).isEqualTo(3);
  }

  @Test
  @Sql("classpath:/sql/folio-entitlement.sql")
  @WireMockStub(scripts = {"/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app2/get-discovery.json", "/wiremock/folio-module2/uninstall.json"})
  void verifyModuleDeregistrationRetry() throws Exception {
    var moduleId = "folio-module2-2.0.0";
    var routeId = "mockroute1";

    var wireMockClient = mockBaseDependencies(moduleId, routeId);
    stubGet(wireMockClient, 1, urlMatching("/admin/realms/test/clients/test/authz/resource-server/resource.*"), null,
      500);

    var entitlementRequest = entitlementRequest(FOLIO_APP2_ID);
    var queryParams = Map.of("purge", "true", "ignoreErrors", "true");
    var request =
      delete(updatePathWithPrefix("/entitlements")).contentType(APPLICATION_JSON).header(TOKEN, getSystemAccessToken())
        .content(asJsonString(entitlementRequest));
    queryParams.forEach(request::queryParam);

    mockMvc.perform(request).andExpect(status().isBadRequest()).andExpect(
      jsonPath("$.errors[0].parameters[0].value").value(
        containsString("Failed to remove authorization resources in Keycloak")));

    var endpointsCalled = wireMockClient.getServeEvents().stream().filter(e -> e.getResponse().getStatus() == 500)
      .map(e -> e.getRequest().getUrl()).toList();
    assertThat(endpointsCalled).hasSize(6);
    assertThat(endpointsCalled.stream().filter(
        v -> v.equals("/admin/realms/test/clients/test/authz/resource-server/resource?name=%2Ffolio-module2%2Fevents"))
      .count()).isEqualTo(3);
    assertThat(endpointsCalled.stream().filter(v -> v.equals(
        "/admin/realms/test/clients/test/authz/resource-server/resource?name=%2Ffolio-module2%2Fevents%2F%7Bid%7D"))
      .count()).isEqualTo(3);
  }

  protected WireMock mockBaseDependencies(String moduleId, String routeId) throws Exception {
    var wireMockClient = getWireMockClient();
    var mockServiceInfo = new Service();
    mockServiceInfo.setId(moduleId);

    stubGet(wireMockClient, 1, urlMatching("/services/" + moduleId), mockServiceInfo);

    var routesResponseMock = new KongResultList<Route>();
    var route = new Route();
    route.setId(routeId);
    routesResponseMock.setData(List.of(route));
    stubGet(wireMockClient, 1, urlMatching("/routes\\?tags=test%2C" + moduleId + "(&offset=0)?"), routesResponseMock);

    var clientRepMock = new ClientRepresentation();
    clientRepMock.setId("test");
    clientRepMock.setClientId("test");
    clientRepMock.setName("test");
    stubGet(wireMockClient, 1, urlEqualTo("/admin/realms/test/clients?clientId=test"), List.of(clientRepMock));

    var scopeRepMock = new ScopeRepresentation();
    scopeRepMock.setId("test");
    scopeRepMock.setName("test");
    stubGet(wireMockClient, 1, urlEqualTo("/admin/realms/test/clients/test/authz/resource-server/scope"),
      List.of(scopeRepMock));
    return wireMockClient;
  }

  static class TestConfiguration {

    @Bean
    @Primary
    public KeycloakConfigurationProperties keycloakConfigurationProperties() {
      var result = new KeycloakConfigurationProperties();
      result.setUrl(wmAdminClient.getWireMockUrl());
      result.setEnabled(true);
      result.setLogin(new Login());
      result.getLogin().setClientNameSuffix("");
      return result;
    }

    @Bean
    @Primary
    public KeycloakProperties keycloakProperties() {
      var result = new KeycloakProperties();
      result.setUrl(wmAdminClient.getWireMockUrl());
      var adminProps = new KeycloakAdminProperties();
      result.setAdmin(adminProps);
      adminProps.setClientId("mgr-component-app");
      adminProps.setUsername("admin");
      adminProps.setPassword("secret");
      return result;
    }

    @Bean
    @Primary
    public Keycloak keycloakWiremock() {
      return buildKeycloakAdminClient("secret", keycloakProperties());
    }

    @Bean
    @Primary
    public KeycloakService keycloakServiceWiremock(Keycloak keycloakWiremock, KeycloakModuleDescriptorMapper mapper,
      KeycloakRetrySupportService keycloakRetrySupportService) {
      return new KeycloakService(keycloakWiremock, mapper, keycloakConfigurationProperties(),
        keycloakRetrySupportService);
    }
  }
}
