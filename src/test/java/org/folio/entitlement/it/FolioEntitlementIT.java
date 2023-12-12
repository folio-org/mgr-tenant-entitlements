package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.support.KafkaEventAssertions.assertEntitlementEvents;
import static org.folio.entitlement.support.KeycloakTestClientConfiguration.KeycloakTestClient.ALL_HTTP_METHODS;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.entitlementTopic;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestValues.emptyEntitlements;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.folio.entitlement.support.TestValues.entitlementWithModules;
import static org.folio.entitlement.support.TestValues.entitlements;
import static org.folio.entitlement.support.TestValues.extendedEntitlement;
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.folio.entitlement.support.TestValues.queryByTenantAndAppId;
import static org.folio.test.TestConstants.OKAPI_AUTH_TOKEN;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.support.KeycloakTestClientConfiguration;
import org.folio.entitlement.support.KeycloakTestClientConfiguration.KeycloakTestClient;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableKeycloak;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@EnableKeycloak
@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
@TestPropertySource(properties = {
  "application.keycloak.enabled=true",
  "application.okapi.enabled=false",
  "application.kong.enabled=false",
  "application.clients.folio.connect-timeout=250ms",
  "application.clients.folio.read-timeout=250ms"
})
@Import(KeycloakTestClientConfiguration.class)
class FolioEntitlementIT extends BaseIntegrationTest {

  private static final String FOLIO_APP1_ID = "folio-app1-1.0.0";
  private static final String FOLIO_APP2_ID = "folio-app2-2.0.0";
  private static final String FOLIO_APP5_ID = "folio-app5-5.0.0";
  private static final String FOLIO_MODULE1_ID = "folio-module1-1.0.0";

  @Autowired private KeycloakTestClient keycloakTestClient;

  @BeforeAll
  static void beforeAll(@Autowired ApplicationContext appContext) {
    fakeKafkaConsumer.registerTopic(entitlementTopic(), EntitlementEvent.class);

    assertThat(appContext.containsBean("folioModuleInstallerFlowProvider")).isTrue();
    assertThat(appContext.containsBean("keycloakAuthResourceCreator")).isTrue();
    assertThat(appContext.containsBean("keycloakAuthResourceCleaner")).isTrue();
    assertThat(appContext.containsBean("okapiModuleInstaller")).isFalse();
    assertThat(appContext.containsBean("kongRouteCreator")).isFalse();
    assertThat(appContext.containsBean("kongRouteCleaner")).isFalse();
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json"
  })
  void install_positive_freshInstallation() throws Exception {
    var entitlementRequest = entitlementRequest(FOLIO_APP1_ID);
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    entitleApplications(entitlementRequest, queryParams, extendedEntitlements(extendedEntitlement(FOLIO_APP1_ID)));

    var modules = List.of(FOLIO_MODULE1_ID);
    var expected = entitlements(entitlementWithModules(TENANT_ID, FOLIO_APP1_ID, modules));
    var expectedModuleEntitlements = entitlements(entitlement(TENANT_ID, FOLIO_APP1_ID));
    getEntitlementsWithModules(queryByTenantAndAppId(FOLIO_APP1_ID), expected);
    getModuleEntitlements(FOLIO_MODULE1_ID, expectedModuleEntitlements);
    assertEntitlementEvents(List.of(new EntitlementEvent(ENTITLE.name(), FOLIO_MODULE1_ID, TENANT_NAME, TENANT_ID)));
    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME))
      .containsExactly("/folio-module1/events", "/folio-module1/events/{id}");
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app4/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app4/get.json",
    "/wiremock/mgr-applications/folio-app4/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json"
  })
  void install_positive_freshInstallationModuleWithoutTenantApi() throws Exception {
    var applicationId = "folio-app4-4.0.0";
    var entitlementRequest = entitlementRequest(applicationId);
    var moduleId = "folio-module4-4.0.0";
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    entitleApplications(entitlementRequest, queryParams, extendedEntitlements(extendedEntitlement(applicationId)));

    var modules = List.of(moduleId);
    var expected = entitlements(entitlementWithModules(TENANT_ID, applicationId, modules));
    var expectedModuleEntitlements = entitlements(entitlement(TENANT_ID, applicationId));
    getEntitlementsWithModules(queryByTenantAndAppId(applicationId), expected);
    getModuleEntitlements(moduleId, expectedModuleEntitlements);
    assertEntitlementEvents(List.of(new EntitlementEvent(ENTITLE.name(), moduleId, TENANT_NAME, TENANT_ID)));
    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly("/folio-module4/events");
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @Sql(scripts = "/sql/module-entitlement.sql")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json"
  })
  void install_positive_alreadyInstalled() throws Exception {
    var entitlementRequest = entitlementRequest(FOLIO_APP1_ID);
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    entitleApplications(entitlementRequest, queryParams, extendedEntitlements(extendedEntitlement(FOLIO_APP1_ID)));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP1_ID), entitlements(entitlement(FOLIO_APP1_ID)));
    var expectedModuleEntitlements = entitlements(entitlement(FOLIO_APP1_ID));
    getModuleEntitlements(FOLIO_MODULE1_ID, expectedModuleEntitlements);

    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME))
      .containsExactly("/folio-module1/events", "/folio-module1/events/{id}");
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @Sql(scripts = "/sql/entitlement-flow-exists.sql")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json"
  })
  void install_positive_alreadyInstalled_anotherVersion() throws Exception {
    mockMvc.perform(post("/entitlements")
        .queryParam("tenantParameters", "loadReference=true")
        .queryParam("ignoreErrors", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(FOLIO_APP1_ID))))
      .andDo(logResponseBody())
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", containsString("Found validation errors in entitlement request")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("folio-app1-1.1.0")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("Entitle flow finished")));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP1_ID), emptyEntitlements());
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get-404.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json"
  })
  void install_negative_tenantIsNotFound() throws Exception {
    mockMvc.perform(post("/entitlements")
        .queryParam("tenantParameters", "loadReference=true")
        .queryParam("ignoreErrors", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(FOLIO_APP1_ID))))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].type", is("FlowExecutionException")))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Application flow '.+' executed with status: FAILED")))
      .andExpect(jsonPath("$.errors[0].parameters[1].key", is("TenantLoader")))
      .andExpect(jsonPath("$.errors[0].parameters[1].value", is(
        "FAILED: [EntityNotFoundException] Tenant is not found: 6ad28dae-7c02-4f89-9320-153c55bf1914")));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP1_ID), emptyEntitlements());
  }

  @Test
  @Sql(scripts = "/sql/truncate-tables.sql")
  @WireMockStub("/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json")
  void install_negative_dependencyValidationFailed() throws Exception {
    mockMvc.perform(post("/entitlements")
        .queryParam("tenantParameters", "loadReference=true")
        .queryParam("ignoreErrors", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(FOLIO_APP2_ID))))
      .andDo(logResponseBody())
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", containsString("Missing dependencies found for the applications")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is(FOLIO_APP2_ID)))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("folio-module1-api 1.0.0")));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP2_ID), emptyEntitlements());
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app5/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app5/get.json",
    "/wiremock/mgr-applications/folio-app5/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module1/uninstall.json",
    "/wiremock/folio-module2/install-timeout.json"
  })
  void install_negative_readTimeoutOnModuleInstallation() throws Exception {
    mockMvc.perform(post("/entitlements")
        .queryParam("tenantParameters", "loadReference=true")
        .queryParam("ignoreErrors", "false")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(FOLIO_APP5_ID))))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].type", is("FlowCancelledException")))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern(
        "Application flow '.+' executed with status: CANCELLED")))
      .andExpect(jsonPath("$.errors[0].parameters[7].key", is("KeycloakAuthResourceCreator")))
      .andExpect(jsonPath("$.errors[0].parameters[7].value", is("CANCELLED")))
      .andExpect(jsonPath("$.errors[0].parameters[8].key", is("folio-module1-1.0.0-moduleInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[8].value", is("CANCELLED")))
      .andExpect(jsonPath("$.errors[0].parameters[10].key", is("folio-module2-2.0.0-moduleInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[10].value", matchesPattern(
        "FAILED: \\[IntegrationException] \\[HttpTimeoutException] Failed to perform request "
          + "\\[method: POST, uri: http://localhost:\\d+/folio-module2/_/tenant], cause: request timed out")));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP5_ID), emptyEntitlements());

    assertEntitlementEvents(List.of(
      new EntitlementEvent(ENTITLE.name(), FOLIO_MODULE1_ID, TENANT_NAME, TENANT_ID),
      new EntitlementEvent(REVOKE.name(), FOLIO_MODULE1_ID, TENANT_NAME, TENANT_ID)));

    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).isEmpty();
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app5/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app5/get.json",
    "/wiremock/mgr-applications/folio-app5/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module2/install-timeout.json"
  })
  void install_negative_readTimeoutOnModuleInstallationWithIgnoreErrors() throws Exception {
    mockMvc.perform(post("/entitlements")
        .queryParam("tenantParameters", "loadReference=true")
        .queryParam("ignoreErrors", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(FOLIO_APP5_ID))))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Application flow '.+' executed with status: FAILED")))
      .andExpect(jsonPath("$.errors[0].parameters[8].key", is("folio-module1-1.0.0-moduleInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[8].value", is("FINISHED")))
      .andExpect(jsonPath("$.errors[0].parameters[10].key", is("folio-module2-2.0.0-moduleInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[10].value", matchesPattern(
        "FAILED: \\[IntegrationException] \\[HttpTimeoutException] Failed to perform request "
          + "\\[method: POST, uri: http://localhost:\\d+/folio-module2/_/tenant], cause: request timed out")));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP5_ID), emptyEntitlements());
    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      "/folio-module1/events", "/folio-module1/events/{id}", "/folio-module2/events", "/folio-module2/events/{id}");
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app5/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app5/get.json",
    "/wiremock/mgr-applications/folio-app5/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module2/install-no-response.json.json"
  })
  void install_negative_emptyResponse() throws Exception {
    mockMvc.perform(post("/entitlements")
        .queryParam("tenantParameters", "loadReference=true")
        .queryParam("ignoreErrors", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(FOLIO_APP5_ID))))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Application flow '.+' executed with status: FAILED")))
      .andExpect(jsonPath("$.errors[0].parameters[8].key", is("folio-module1-1.0.0-moduleInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[8].value", is("FINISHED")))
      .andExpect(jsonPath("$.errors[0].parameters[10].key", is("folio-module2-2.0.0-moduleInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[10].value", matchesPattern(
        "FAILED: \\[IntegrationException] \\[IOException] Failed to perform request "
          + "\\[method: POST, uri: http://localhost:\\d+/folio-module2/_/tenant], "
          + "cause: HTTP/1.1 header parser received no bytes")));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP5_ID), emptyEntitlements());
    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      "/folio-module1/events", "/folio-module1/events/{id}", "/folio-module2/events", "/folio-module2/events/{id}");
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app2/get.json",
    "/wiremock/mgr-applications/folio-app2/get-discovery.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module2/uninstall.json"
  })
  @Sql(executionPhase = BEFORE_TEST_METHOD, scripts = "classpath:/sql/folio-entitlement.sql")
  void uninstall_positive() throws Exception {
    var moduleId = "folio-module2-2.0.0";
    var entitlementRequest = entitlementRequest(FOLIO_APP2_ID);
    var queryParams = Map.of("purge", "true", "ignoreErrors", "true");

    var expectedEntitlements = extendedEntitlements(extendedEntitlement(FOLIO_APP2_ID));
    revokeEntitlements(entitlementRequest, queryParams, expectedEntitlements);
    assertEntitlementEvents(List.of(new EntitlementEvent(REVOKE.name(), moduleId, TENANT_NAME, TENANT_ID)));
    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP2_ID), emptyEntitlements());
    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).isEmpty();
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).isEmpty();
  }
}
