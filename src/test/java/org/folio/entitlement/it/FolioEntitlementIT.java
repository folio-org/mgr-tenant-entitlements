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
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.folio.entitlement.support.TestValues.queryByTenantAndAppId;
import static org.folio.test.TestConstants.OKAPI_AUTH_TOKEN;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.support.KeycloakTestClientConfiguration;
import org.folio.entitlement.support.KeycloakTestClientConfiguration.KeycloakTestClient;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.entitlement.support.model.AuthorizationResource;
import org.folio.test.extensions.EnableKeycloakTlsMode;
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

@EnableKeycloakTlsMode
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
  private static final String FOLIO_APP3_ID = "folio-app3-3.0.0";
  private static final String FOLIO_APP5_ID = "folio-app5-5.0.0";
  private static final String FOLIO_MODULE1_ID = "folio-module1-1.0.0";
  private static final String FOLIO_MODULE3_ID = "folio-module3-3.0.0";

  private static final String FOLIO_APP6_V1_ID = "folio-app6-6.0.0";
  private static final String FOLIO_APP6_V2_ID = "folio-app6-6.1.0";

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
    assertThat(appContext.containsBean("folioKongAdminClient")).isFalse();
    assertThat(appContext.containsBean("folioKongGatewayService")).isFalse();
    assertThat(appContext.containsBean("folioKongModuleRegistrar")).isFalse();
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json"
  })
  void install_positive_freshInstallation() throws Exception {
    var entitlementRequest = entitlementRequest(FOLIO_APP1_ID);
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    entitleApplications(entitlementRequest, queryParams, extendedEntitlements(entitlement(FOLIO_APP1_ID)));

    var modules = List.of(FOLIO_MODULE1_ID);
    var expected = entitlements(entitlementWithModules(TENANT_ID, FOLIO_APP1_ID, modules));
    var expectedModuleEntitlements = entitlements(entitlement(TENANT_ID, FOLIO_APP1_ID));
    assertEntitlementsWithModules(queryByTenantAndAppId(FOLIO_APP1_ID), expected);
    assertModuleEntitlements(FOLIO_MODULE1_ID, expectedModuleEntitlements);
    assertEntitlementEvents(List.of(new EntitlementEvent(ENTITLE.name(), FOLIO_MODULE1_ID, TENANT_NAME, TENANT_ID)));
    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      AuthorizationResource.of("/folio-module1/events", "GET", "POST"),
      AuthorizationResource.of("/folio-module1/events/{id}", "GET"));
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app4/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app4/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json"
  })
  void install_positive_freshInstallationModuleWithoutTenantApi() throws Exception {
    var applicationId = "folio-app4-4.0.0";
    var entitlementRequest = entitlementRequest(applicationId);
    var moduleId = "folio-module4-4.0.0";
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    entitleApplications(entitlementRequest, queryParams, extendedEntitlements(entitlement(applicationId)));

    var modules = List.of(moduleId);
    var expected = entitlements(entitlementWithModules(TENANT_ID, applicationId, modules));
    var expectedModuleEntitlements = entitlements(entitlement(TENANT_ID, applicationId));
    assertEntitlementsWithModules(queryByTenantAndAppId(applicationId), expected);
    assertModuleEntitlements(moduleId, expectedModuleEntitlements);
    assertEntitlementEvents(List.of(new EntitlementEvent(ENTITLE.name(), moduleId, TENANT_NAME, TENANT_ID)));
    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      AuthorizationResource.of("/folio-module4/events", "GET"));
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @Sql(scripts = "/sql/module-entitlement.sql")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json"
  })
  void install_positive_alreadyInstalled() throws Exception {
    var entitlementRequest = entitlementRequest(FOLIO_APP1_ID);
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    entitleApplications(entitlementRequest, queryParams, extendedEntitlements(entitlement(FOLIO_APP1_ID)));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP1_ID), entitlements(entitlement(FOLIO_APP1_ID)));
    var expectedModuleEntitlements = entitlements(entitlement(FOLIO_APP1_ID));
    assertModuleEntitlements(FOLIO_MODULE1_ID, expectedModuleEntitlements);

    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      AuthorizationResource.of("/folio-module1/events", "GET", "POST"),
      AuthorizationResource.of("/folio-module1/events/{id}", "GET"));
    assertEntitlementEvents(List.of(new EntitlementEvent(ENTITLE.name(), FOLIO_MODULE1_ID, TENANT_NAME, TENANT_ID)));
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @Sql(scripts = "/sql/entitlement-flow-exists.sql")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
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
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: FAILED")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("ApplicationFlowValidator")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(
        "FAILED: [RequestValidationException] Found validation errors in entitlement request, "
          + "parameters: [{key: folio-app1-1.1.0, value: Entitle flow finished}]")));

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
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: FAILED")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("TenantLoader")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(
        "FAILED: [EntityNotFoundException] Tenant is not found: 6ad28dae-7c02-4f89-9320-153c55bf1914")));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP1_ID), emptyEntitlements());
  }

  @Test
  @Sql(scripts = "/sql/truncate-tables.sql")
  @WireMockStub({
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json"
  })
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
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: FAILED")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("InterfaceIntegrityValidator")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is("FAILED: [RequestValidationException] "
        + "Missing dependencies found for the applications, "
        + "parameters: [{key: folio-app2-2.0.0, value: folio-module1-api 1.0.0}]")));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP2_ID), emptyEntitlements());
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app5/get-by-ids-query-full.json",
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
        .queryParam("purgeOnRollback", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(FOLIO_APP5_ID))))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].type", is("FlowCancelledException")))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: CANCELLED")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("folio-module2-2.0.0-moduleInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", matchesPattern(
        "FAILED: \\[IntegrationException] \\[HttpTimeoutException] Failed to perform request "
          + "\\[method: POST, uri: http://.+:\\d+/folio-module2/_/tenant], cause: request timed out")));

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
    "/wiremock/mgr-applications/folio-app5/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module1/uninstall-timeout.json",
    "/wiremock/folio-module2/install-timeout.json"
  })
  void install_negative_readTimeoutOnModuleCancellation() throws Exception {
    mockMvc.perform(post("/entitlements")
        .queryParam("tenantParameters", "loadReference=true")
        .queryParam("ignoreErrors", "false")
        .queryParam("purgeOnRollback", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(FOLIO_APP5_ID))))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].type", is("FlowCancellationException")))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: CANCELLATION_FAILED")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("folio-module2-2.0.0-moduleInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", matchesPattern(
        "FAILED: \\[IntegrationException] \\[HttpTimeoutException] Failed to perform request "
          + "\\[method: POST, uri: http://.+:\\d+/folio-module2/_/tenant], cause: request timed out")))
      .andExpect(jsonPath("$.errors[0].parameters[1].key", is("folio-module1-1.0.0-moduleInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[1].value", matchesPattern(
        "CANCELLATION_FAILED: \\[IntegrationException] \\[HttpTimeoutException] Failed to perform request "
          + "\\[method: POST, uri: http://.+:\\d+/folio-module1/_/tenant], cause: request timed out")));

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
    "/wiremock/mgr-applications/folio-app5/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module2/install-timeout.json"
  })
  void install_negative_readTimeoutOnModuleInstallationNoPurgeOnRollback() throws Exception {
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
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: CANCELLED")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("folio-module2-2.0.0-moduleInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", matchesPattern(
        "FAILED: \\[IntegrationException] \\[HttpTimeoutException] Failed to perform request "
          + "\\[method: POST, uri: http://.+:\\d+/folio-module2/_/tenant], cause: request timed out")));

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
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: FAILED")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("folio-module2-2.0.0-moduleInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", matchesPattern(
        "FAILED: \\[IntegrationException] \\[HttpTimeoutException] Failed to perform request "
          + "\\[method: POST, uri: http://.+:\\d+/folio-module2/_/tenant], cause: request timed out")));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP5_ID), emptyEntitlements());
    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      AuthorizationResource.of("/folio-module1/events", "GET", "POST"),
      AuthorizationResource.of("/folio-module1/events/{id}", "GET"),
      AuthorizationResource.of("/folio-module2/events", "GET", "POST"),
      AuthorizationResource.of("/folio-module2/events/{id}", "GET"));
    assertEntitlementEvents(List.of(new EntitlementEvent(ENTITLE.name(), FOLIO_MODULE1_ID, TENANT_NAME, TENANT_ID)));
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app5/get-by-ids-query-full.json",
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
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: FAILED")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("folio-module2-2.0.0-moduleInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", matchesPattern(
        "FAILED: \\[IntegrationException] \\[IOException] Failed to perform request "
          + "\\[method: POST, uri: http://.+:\\d+/folio-module2/_/tenant], "
          + "cause: HTTP/1.1 header parser received no bytes")));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP5_ID), emptyEntitlements());
    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      AuthorizationResource.of("/folio-module1/events", "GET", "POST"),
      AuthorizationResource.of("/folio-module1/events/{id}", "GET"),
      AuthorizationResource.of("/folio-module2/events", "GET", "POST"),
      AuthorizationResource.of("/folio-module2/events/{id}", "GET"));
    assertEntitlementEvents(List.of(new EntitlementEvent(ENTITLE.name(), FOLIO_MODULE1_ID, TENANT_NAME, TENANT_ID)));
  }

  @Test
  @Sql("classpath:/sql/folio-entitlement.sql")
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app2/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app2/get-discovery.json",
    "/wiremock/folio-module2/uninstall.json"
  })
  void uninstall_positive() throws Exception {
    var moduleId = "folio-module2-2.0.0";
    var entitlementRequest = entitlementRequest(FOLIO_APP2_ID);
    var queryParams = Map.of("purge", "true", "ignoreErrors", "true");

    var expectedEntitlements = extendedEntitlements(entitlement(FOLIO_APP2_ID));
    revokeEntitlements(entitlementRequest, queryParams, expectedEntitlements);
    assertEntitlementEvents(List.of(new EntitlementEvent(REVOKE.name(), moduleId, TENANT_NAME, TENANT_ID)));
    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP2_ID), emptyEntitlements());
    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).isEmpty();
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).isEmpty();
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @Sql("classpath:/sql/folio-entitlement-dependent.sql")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app-mixed/get-by-ids-13-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json",
    "/wiremock/mgr-applications/folio-app3/get-discovery.json",
    "/wiremock/folio-module1/uninstall.json",
    "/wiremock/folio-module3/uninstall.json"
  })
  void uninstall_positive_dependentApps() throws Exception {
    var entitlementRequest = entitlementRequest(TENANT_ID, FOLIO_APP1_ID, FOLIO_APP3_ID);
    var queryParams = Map.of("purge", "true", "ignoreErrors", "true");

    var expectedEntitlements = extendedEntitlements(entitlement(FOLIO_APP1_ID), entitlement(FOLIO_APP3_ID));

    revokeEntitlements(entitlementRequest, queryParams, expectedEntitlements);

    assertEntitlementEvents(List.of(
      new EntitlementEvent(REVOKE.name(), FOLIO_MODULE1_ID, TENANT_NAME, TENANT_ID),
      new EntitlementEvent(REVOKE.name(), FOLIO_MODULE3_ID, TENANT_NAME, TENANT_ID)
    ));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP1_ID), emptyEntitlements());
    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP3_ID), emptyEntitlements());

    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).isEmpty();
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).isEmpty();
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @Sql("classpath:/sql/folio-entitlement-dependent.sql")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app1/get-discovery.json"
  })
  void uninstall_negative_dependentApps() throws Exception {
    mockMvc.perform(delete("/entitlements")
        .queryParam("purge", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(FOLIO_APP1_ID))))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: FAILED")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("RevokeRequestDependencyValidator")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", containsString(
        "The following applications must be uninstalled first: [" + FOLIO_APP3_ID + "]")));

    var entitlementQuery = String.format("applicationId==(%s or %s)", FOLIO_APP1_ID, FOLIO_APP3_ID);
    getEntitlementsByQuery(entitlementQuery, entitlements(entitlement(FOLIO_APP1_ID), entitlement(FOLIO_APP3_ID)));
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app6/v1-get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app6/v2-get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app6/v1-get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module2/install.json"
  })
  void upgrade_positive_freshInstallation() throws Exception {
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");

    var entitlementRequest = entitlementRequest(FOLIO_APP6_V1_ID);
    entitleApplications(entitlementRequest, queryParams, extendedEntitlements(entitlement(FOLIO_APP6_V1_ID)));
    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      AuthorizationResource.of("/folio-module1/events", "GET", "POST"),
      AuthorizationResource.of("/folio-module1/events/{id}", "GET"),
      AuthorizationResource.of("/folio-module2/events", "GET", "POST"),
      AuthorizationResource.of("/folio-module2/events/{id}", "GET"));

    var upgradeRequest = entitlementRequest(FOLIO_APP6_V2_ID);
    upgradeApplications(upgradeRequest, queryParams, extendedEntitlements(entitlement(FOLIO_APP6_V2_ID)));
    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      AuthorizationResource.of("/folio-module1/events", "GET", "POST"),
      AuthorizationResource.of("/folio-module1/events/{id}", "GET"),
      AuthorizationResource.of("/folio-module2/events", "GET", "PUT"),
      AuthorizationResource.of("/folio-module2/events/{id}", "GET"),
      AuthorizationResource.of("/folio-module2/v2/events", "POST"));
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app6/v1-get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app6/v2-get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app6/v1-get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module1/install.json",
    "/wiremock/folio-module2/install.json"
  })
  void upgrade_negative_applicationIsNotInstalled() throws Exception {
    mockMvc.perform(put("/entitlements")
        .queryParam("tenantParameters", "loadReference=true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(FOLIO_APP6_V2_ID))))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: FAILED")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("UpgradeRequestValidator")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(
        "FAILED: [RequestValidationException] Invalid applications provided for upgrade, "
          + "parameters: [{key: folio-app6-6.1.0, value: Entitlement is not found for application}]")));

    getEntitlementsByQuery(queryByTenantAndAppId(FOLIO_APP6_V2_ID), emptyEntitlements());

    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).isEmpty();
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).isEmpty();
  }
}
