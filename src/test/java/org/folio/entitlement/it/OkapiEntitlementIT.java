package org.folio.entitlement.it;

import static java.lang.Boolean.TRUE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.Duration.ofMillis;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.support.KafkaEventAssertions.assertCapabilityEvents;
import static org.folio.entitlement.support.KafkaEventAssertions.assertEntitlementEvents;
import static org.folio.entitlement.support.KafkaEventAssertions.assertScheduledJobEvents;
import static org.folio.entitlement.support.KafkaEventAssertions.assertSystemUserEvents;
import static org.folio.entitlement.support.KeycloakTestClientConfiguration.KeycloakTestClient.ALL_HTTP_METHODS;
import static org.folio.entitlement.support.TestConstants.COMMON_KEYCLOAK_INTEGRATION_BEAN_TYPES;
import static org.folio.entitlement.support.TestConstants.COMMON_KONG_INTEGRATION_BEAN_TYPES;
import static org.folio.entitlement.support.TestConstants.FOLIO_KEYCLOAK_INTEGRATION_BEAN_TYPES;
import static org.folio.entitlement.support.TestConstants.FOLIO_KONG_INTEGRATION_BEAN_TYPES;
import static org.folio.entitlement.support.TestConstants.FOLIO_MODULE_INSTALLER_BEAN_TYPES;
import static org.folio.entitlement.support.TestConstants.IGNORE_ERRORS;
import static org.folio.entitlement.support.TestConstants.OKAPI_KEYCLOAK_INTEGRATION_BEAN_TYPES;
import static org.folio.entitlement.support.TestConstants.OKAPI_KONG_INTEGRATION_BEAN_TYPES;
import static org.folio.entitlement.support.TestConstants.OKAPI_MODULE_INSTALLER_BEAN_TYPES;
import static org.folio.entitlement.support.TestConstants.PURGE;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.TENANT_PARAMETERS;
import static org.folio.entitlement.support.TestConstants.capabilitiesTenantTopic;
import static org.folio.entitlement.support.TestConstants.entitlementTopic;
import static org.folio.entitlement.support.TestConstants.scheduledJobsTenantTopic;
import static org.folio.entitlement.support.TestConstants.systemUserTenantTopic;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestUtils.parseResponse;
import static org.folio.entitlement.support.TestUtils.readCapabilityEvent;
import static org.folio.entitlement.support.TestUtils.readScheduledJobEvent;
import static org.folio.entitlement.support.TestUtils.readSystemUserEvent;
import static org.folio.entitlement.support.TestValues.emptyEntitlements;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.entitlementEvent;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.folio.entitlement.support.TestValues.entitlementWithModules;
import static org.folio.entitlement.support.TestValues.entitlements;
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.folio.entitlement.support.TestValues.queryByTenantAndAppId;
import static org.folio.entitlement.support.model.AuthorizationResource.authResource;
import static org.folio.test.TestConstants.OKAPI_AUTH_TOKEN;
import static org.folio.test.extensions.impl.WireMockExtension.WM_DOCKER_PORT;
import static org.folio.test.extensions.impl.WireMockExtension.WM_NETWORK_ALIAS;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

import feign.FeignException.BadGateway;
import feign.Request;
import feign.Request.HttpMethod;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.SneakyThrows;
import org.folio.entitlement.domain.dto.ApplicationFlows;
import org.folio.entitlement.domain.dto.EntitlementRequestBody;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.ExtendedEntitlements;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.support.KeycloakTestClientConfiguration;
import org.folio.entitlement.support.KeycloakTestClientConfiguration.KeycloakTestClient;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableKeycloakTlsMode;
import org.folio.test.extensions.EnableOkapiSecurity;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.folio.tools.kong.client.KongAdminClient;
import org.folio.tools.kong.model.Route;
import org.folio.tools.kong.model.Service;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@IntegrationTest
@EnableOkapiSecurity
@SqlMergeMode(MERGE)
@EnableKeycloakTlsMode
@TestPropertySource(properties = {
  "application.okapi.enabled=true",
  "application.kong.enabled=true",
  "application.keycloak.enabled=true",
  "application.security.enabled=false",
})
@Import(KeycloakTestClientConfiguration.class)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "/sql/truncate-tables.sql")
class OkapiEntitlementIT extends BaseIntegrationTest {

  private static final String OKAPI_APP_ID = "okapi-app-1.0.0";
  private static final String OKAPI_APP_3_ID = "okapi-app3-3.0.0";
  private static final String OKAPI_APP_4_ID = "okapi-app4-4.0.0";
  private static final String OKAPI_APP_5_ID = "okapi-app5-5.0.0";
  private static final String OKAPI_MODULE_ID = "okapi-module-1.0.0";
  private static final String OKAPI_MODULE_3_ID = "okapi-module3-1.0.0";
  private static final String OKAPI_MODULE_4_ID = "okapi-module4-4.0.0";
  private static final String OKAPI_MODULE_5_ID = "okapi-module5-5.0.0";

  private static final String FOLIO_MODULE1_ID = "folio-module1-1.0.0";
  private static final String FOLIO_MODULE2_ID = "folio-module2-2.0.0";
  private static final String FOLIO_MODULE2_V2_ID = "folio-module2-2.1.0";

  private static final List<String> MODULES = List.of(
    OKAPI_MODULE_ID, OKAPI_MODULE_3_ID, OKAPI_MODULE_4_ID, OKAPI_MODULE_5_ID,
    FOLIO_MODULE1_ID, FOLIO_MODULE2_ID, FOLIO_MODULE2_V2_ID);

  private static final String INVALID_ROUTE_HASH = "4737040f862ad8b9cad357726503dae4952836e7";

  @Autowired private KeycloakTestClient keycloakTestClient;
  @SpyBean private KongAdminClient kongAdminClient;

  @BeforeAll
  static void beforeAll(@Autowired KongAdminClient kongAdminClient, @Autowired ApplicationContext appContext) {
    checkApplicationContextBeans(appContext);

    var wiremockUrl = String.format("http://%s:%s", WM_NETWORK_ALIAS, WM_DOCKER_PORT);
    MODULES.forEach(moduleId -> kongAdminClient.upsertService(moduleId, new Service().name(moduleId).url(wiremockUrl)));

    fakeKafkaConsumer.registerTopic(entitlementTopic(), EntitlementEvent.class);
    fakeKafkaConsumer.registerTopic(capabilitiesTenantTopic(), ResourceEvent.class);
    fakeKafkaConsumer.registerTopic(scheduledJobsTenantTopic(), ResourceEvent.class);
    fakeKafkaConsumer.registerTopic(systemUserTenantTopic(), ResourceEvent.class);
  }

  @AfterEach
  void tearDown() {
    Mockito.reset(kongAdminClient);
    for (var kr : kongAdminClient.getRoutesByTag(TENANT_NAME, null)) {
      kongAdminClient.deleteRoute(kr.getService().getId(), kr.getId());
    }
  }

  @AfterAll
  static void tearDown(@Autowired KongAdminClient kongAdminClient) {
    MODULES.forEach(kongAdminClient::deleteService);
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @Sql(scripts = "classpath:/sql/okapi-app-installed.sql")
  void get_positive() throws Exception {
    var expectedEntitlements =
      entitlements(entitlementWithModules(TENANT_ID, OKAPI_APP_ID, List.of(OKAPI_MODULE_ID)));
    assertEntitlementsWithModules(queryByTenantAndAppId(OKAPI_APP_ID), expectedEntitlements);

    var expectedModuleEntitlements = entitlements(entitlement(TENANT_ID, OKAPI_APP_ID));
    mockMvc.perform(get("/entitlements/modules/{moduleId}", OKAPI_MODULE_ID)
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .queryParam("includeModules", "true"))
      .andExpect(status().isOk())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(content().json(asJsonString(expectedModuleEntitlements)));
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/okapi-app/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/okapi-app5/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/okapi-app/get-discovery.json",
    "/wiremock/mgr-applications/okapi-app5/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/okapi/_proxy/install-okapi-module.json",
    "/wiremock/okapi/_proxy/install-okapi-module5.json"
  })
  void install_positive_freshInstallation() throws Exception {
    // install independent application
    var entitlement1 = entitlementWithModules(TENANT_ID, OKAPI_APP_ID, List.of(OKAPI_MODULE_ID));
    entitleApplications(entitlementRequest(OKAPI_APP_ID), emptyMap(), extendedEntitlements(entitlement(OKAPI_APP_ID)));
    assertEntitlementsWithModules(queryByTenantAndAppId(OKAPI_APP_ID), entitlements(entitlement1));
    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null)).hasSize(3);
    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      authResource("/okapi-module/entities", "GET"), authResource("/okapi-module/entities/{id}", "PUT"));

    // entitle dependent application
    var request = entitlementRequest(OKAPI_APP_5_ID);
    entitleApplications(request, emptyMap(), extendedEntitlements(entitlement(OKAPI_APP_5_ID)));
    var entitlement2 = entitlementWithModules(TENANT_ID, OKAPI_APP_5_ID, List.of(OKAPI_MODULE_5_ID));
    assertEntitlementsWithModules(queryByTenantAndAppId(OKAPI_APP_5_ID), entitlements(entitlement2));
    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null)).hasSize(4);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      authResource("/okapi-module/entities", "GET"),
      authResource("/okapi-module/entities/{id}", "PUT"),
      authResource("/okapi-module5/entities", "GET"));

    var savedEntitlementQuery = String.format("applicationId==(%s or %s)", OKAPI_APP_ID, OKAPI_APP_5_ID);
    assertEntitlementsWithModules(savedEntitlementQuery, entitlements(entitlement1, entitlement2));
    assertEntitlementEvents(entitlementEvent(ENTITLE, OKAPI_MODULE_ID), entitlementEvent(ENTITLE, OKAPI_MODULE_5_ID));
    assertCapabilityEvents(
      readCapabilityEvent("json/events/okapi-it/okapi-module-capability-event-1.json"),
      readCapabilityEvent("json/events/okapi-it/okapi-module-capability-event-2.json"));
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/okapi-app15/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/okapi-app/get-discovery.json",
    "/wiremock/mgr-applications/okapi-app5/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/okapi/_proxy/install-okapi-module.json",
    "/wiremock/okapi/_proxy/install-okapi-module5.json"
  })
  void install_positive_dependentApplicationsInSingleCall() throws Exception {
    var entitlement1 = entitlementWithModules(TENANT_ID, OKAPI_APP_ID, List.of(OKAPI_MODULE_ID));
    var entitlement2 = entitlementWithModules(TENANT_ID, OKAPI_APP_5_ID, List.of(OKAPI_MODULE_5_ID));

    entitleApplications(entitlementRequest(TENANT_ID, OKAPI_APP_ID, OKAPI_APP_5_ID), emptyMap(),
      extendedEntitlements(entitlement(OKAPI_APP_ID), entitlement(OKAPI_APP_5_ID)));

    var savedEntitlementQuery = String.format("applicationId==(%s or %s)", OKAPI_APP_ID, OKAPI_APP_5_ID);
    assertEntitlementsWithModules(savedEntitlementQuery, entitlements(entitlement1, entitlement2));

    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null)).hasSize(4);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      authResource("/okapi-module/entities", "GET"),
      authResource("/okapi-module/entities/{id}", "PUT"),
      authResource("/okapi-module5/entities", "GET"));

    assertEntitlementEvents(entitlementEvent(ENTITLE, OKAPI_MODULE_ID), entitlementEvent(ENTITLE, OKAPI_MODULE_5_ID));
    assertCapabilityEvents(
      readCapabilityEvent("json/events/okapi-it/okapi-module-capability-event-1.json"),
      readCapabilityEvent("json/events/okapi-it/okapi-module-capability-event-2.json"));
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/okapi-app/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/okapi-app/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/okapi/_proxy/install-okapi-module.json"
  })
  void install_positive_freshInstallationInAsyncMode() throws Exception {
    var request = entitlementRequest(OKAPI_APP_ID);
    var expectedEntitlements = extendedEntitlements(entitlement(OKAPI_APP_ID));
    var mvcResult = entitleApplications(request, Map.of("async", "true"), expectedEntitlements);
    var resultFlowId = parseResponse(mvcResult, ExtendedEntitlements.class).getFlowId();

    await().pollInterval(ofMillis(100)).atMost(FIVE_SECONDS).untilAsserted(() ->
      mockMvc.perform(get("/entitlement-flows/{flowId}", resultFlowId)
          .contentType(APPLICATION_JSON)
          .header(TOKEN, OKAPI_AUTH_TOKEN))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status", is("finished"))));

    var entitlement = entitlementWithModules(TENANT_ID, OKAPI_APP_ID, List.of(OKAPI_MODULE_ID));
    assertEntitlementsWithModules(queryByTenantAndAppId(OKAPI_APP_ID), entitlements(entitlement));
    assertEntitlementEvents(entitlementEvent(ENTITLE, "okapi-module-1.0.0"));
    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null)).hasSize(3);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      authResource("/okapi-module/entities", "GET"), authResource("/okapi-module/entities/{id}", "PUT"));

    assertCapabilityEvents(readCapabilityEvent("json/events/okapi-it/okapi-module-capability-event-1.json"));
    assertScheduledJobEvents(readScheduledJobEvent("json/events/okapi-it/okapi-module1/scheduled-job-event.json"));
    assertSystemUserEvents(readSystemUserEvent("json/events/okapi-it/system-user-event.json"));
  }

  @Test
  @Sql("/sql/okapi-app-installed.sql")
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/okapi/_proxy/uninstall-okapi-module.json",
    "/wiremock/mgr-applications/okapi-app/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/okapi-app/get-discovery.json"
  })
  void uninstall_positive() throws Exception {
    var queryParams = Map.of("tenantParameters", TENANT_PARAMETERS, "purge", "true");

    var entitlementRequest = entitlementRequest(OKAPI_APP_ID);
    var expectedEntitlements = extendedEntitlements(entitlement(OKAPI_APP_ID));
    revokeEntitlements(entitlementRequest, queryParams, expectedEntitlements);
    assertEntitlementEvents(entitlementEvent(REVOKE, OKAPI_MODULE_ID));
    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null)).isEmpty();
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).isEmpty();
    assertEntitlementsWithModules(queryByTenantAndAppId(OKAPI_APP_ID), emptyEntitlements());
  }

  @Test
  @Sql("/sql/okapi-app3-installed.sql")
  @KeycloakRealms("/keycloak/another-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/another/get.json",
    "/wiremock/okapi/_proxy/uninstall-okapi-module4.json",
    "/wiremock/okapi/_proxy/uninstall-okapi-module3.json",
    "/wiremock/mgr-applications/okapi-app34/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/okapi-app3/get-discovery.json",
    "/wiremock/mgr-applications/okapi-app4/get-discovery.json"
  })
  void uninstall_positive_dependentApplicationsInSingleCall() throws Exception {
    var queryParams = Map.of("tenantParameters", TENANT_PARAMETERS, "purge", "true");
    var tenantId = UUID.fromString("82dec29a-927f-4a14-a9ea-dc616fd17a1c");
    var tenantName = "another";
    var entitlementRequest = entitlementRequest(tenantId, OKAPI_APP_3_ID, OKAPI_APP_4_ID);
    var expectedEntitlements = extendedEntitlements(
      entitlement(tenantId, OKAPI_APP_3_ID), entitlement(tenantId, OKAPI_APP_4_ID));

    revokeEntitlements(entitlementRequest, queryParams, expectedEntitlements);

    assertEntitlementEvents(
      entitlementEvent(REVOKE, OKAPI_MODULE_3_ID, tenantName, tenantId),
      entitlementEvent(REVOKE, OKAPI_MODULE_4_ID, tenantName, tenantId));

    assertThat(kongAdminClient.getRoutesByTag(tenantName, null)).isEmpty();
    assertThat(keycloakTestClient.getAuthorizationResources(tenantName)).isEmpty();

    var entitlementQuery = String.format("applicationId==(%s or %s)", OKAPI_APP_3_ID, OKAPI_APP_4_ID);
    assertEntitlementsWithModules(entitlementQuery, emptyEntitlements());
  }

  @Test
  @Sql("/sql/okapi-app-revoked.sql")
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub("/wiremock/mgr-tenants/test/get.json")
  void uninstall_negative_revokedApplication() throws Exception {
    mockMvc.perform(delete("/entitlements")
        .queryParam("purge", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest())))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: FAILED")))
      .andExpect(jsonPath("$.errors[0].type", is("FlowExecutionException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("ExistingEntitlementValidator")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(
        "FAILED: [EntityNotFoundException] Entitlements are not found for applications: [test-app-1.0.0]")));

    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_ID), emptyEntitlements());
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/okapi/_proxy/install-okapi-module.json",
    "/wiremock/okapi/_proxy/install-okapi-module5.json",
    "/wiremock/okapi/_proxy/uninstall-okapi-module.json",
    "/wiremock/okapi/_proxy/uninstall-okapi-module5.json",
    "/wiremock/mgr-applications/okapi-app/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/okapi-app/get-discovery.json",
    "/wiremock/mgr-applications/okapi-app5/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/okapi-app5/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
  })
  void entitleAndRevoke_positive() throws Exception {
    var queryParams = Map.of("tenantParameters", TENANT_PARAMETERS, "ignoreErrors", "true");

    // entitle and verify dependent application
    var expectedExtendedEntitlements = extendedEntitlements(entitlement(OKAPI_APP_ID));
    entitleApplications(entitlementRequest(OKAPI_APP_ID), queryParams, expectedExtendedEntitlements);
    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_ID), entitlements(entitlement(OKAPI_APP_ID)));
    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null)).hasSize(3);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      authResource("/okapi-module/entities", "GET"), authResource("/okapi-module/entities/{id}", "PUT"));
    assertEntitlementEvents(entitlementEvent(ENTITLE, OKAPI_MODULE_ID));

    // entitle application
    var entitlementRequest = entitlementRequest(OKAPI_APP_5_ID);
    entitleApplications(entitlementRequest, queryParams, extendedEntitlements(entitlement(OKAPI_APP_5_ID)));
    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null)).hasSize(4);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      authResource("/okapi-module/entities", "GET"),
      authResource("/okapi-module/entities/{id}", "PUT"),
      authResource("/okapi-module5/entities", "GET"));

    // revoke entitlement for test application
    var revokeParams = Map.of("purge", "true");
    revokeEntitlements(entitlementRequest, revokeParams, extendedEntitlements(entitlement(OKAPI_APP_5_ID)));
    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_5_ID), emptyEntitlements());
    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null)).hasSize(3);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      authResource("/okapi-module/entities", "GET"), authResource("/okapi-module/entities/{id}", "PUT"));

    // entitle test application again
    entitleApplications(entitlementRequest, queryParams, extendedEntitlements(entitlement(OKAPI_APP_5_ID)));
    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_5_ID), entitlements(entitlement(OKAPI_APP_5_ID)));
    assertEntitlementEvents(
      entitlementEvent(ENTITLE, OKAPI_MODULE_ID), entitlementEvent(ENTITLE, OKAPI_MODULE_5_ID),
      entitlementEvent(REVOKE, OKAPI_MODULE_5_ID), entitlementEvent(ENTITLE, OKAPI_MODULE_5_ID));
    assertCapabilityEvents(readCapabilityEvent("json/events/okapi-it/okapi-module-capability-event-1.json"));
    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null)).hasSize(4);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      authResource("/okapi-module/entities", "GET"),
      authResource("/okapi-module/entities/{id}", "PUT"),
      authResource("/okapi-module5/entities", "GET"));
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  void install_negative_invalidTenantId() throws Exception {
    var entitlementRequest = Map.of("tenantId", "invalid", "applications", List.of(OKAPI_APP_ID));

    mockMvc.perform(post("/entitlements")
        .queryParam("tenantParameters", TENANT_PARAMETERS)
        .queryParam("ignoreErrors", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest)))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.errors[0].message",
        containsString("Cannot deserialize value of type `java.util.UUID` from String \"invalid\"")))
      .andExpect(jsonPath("$.errors[0].type", is("HttpMessageNotReadableException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));

    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_ID), emptyEntitlements());
  }

  @Test
  void install_negative_emptyApplications() throws Exception {
    var entitlementRequest = new EntitlementRequestBody().tenantId(TENANT_ID);

    mockMvc.perform(post("/entitlements")
        .queryParam("tenantParameters", TENANT_PARAMETERS)
        .queryParam("purge", String.valueOf(PURGE))
        .queryParam("ignoreErrors", String.valueOf(IGNORE_ERRORS))
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest)))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.errors[0].message", is("size must be between 1 and 25")))
      .andExpect(jsonPath("$.errors[0].type", is("MethodArgumentNotValidException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void install_negative_applicationsNotArray() throws Exception {
    var entitlementRequest = Map.of("tenantId", TENANT_ID, "applications", OKAPI_APP_ID);

    mockMvc.perform(post("/entitlements")
        .queryParam("tenantParameters", TENANT_PARAMETERS)
        .queryParam("ignoreErrors", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest)))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.errors[0].message", containsString("Cannot construct instance of `java.util.ArrayList`")))
      .andExpect(jsonPath("$.errors[0].type", is("HttpMessageNotReadableException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));

    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_ID), emptyEntitlements());
  }

  @Test
  void install_negative_applicationsMissing() throws Exception {
    mockMvc.perform(post("/entitlements")
        .queryParam("tenantParameters", TENANT_PARAMETERS)
        .queryParam("ignoreErrors", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(new EntitlementRequestBody().tenantId(TENANT_ID))))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.errors[0].message", is("size must be between 1 and 25")))
      .andExpect(jsonPath("$.errors[0].type", is("MethodArgumentNotValidException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  @Sql("/sql/okapi-app3-installed.sql")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/another/get.json",
    "/wiremock/mgr-applications/okapi-app4/get-by-ids-query-full.json"
  })
  void uninstall_negative_applicationDependenciesCheck() throws Exception {
    var applicationId = "okapi-app4-4.0.0";
    var tenantId = UUID.fromString("82dec29a-927f-4a14-a9ea-dc616fd17a1c");
    var entitlementRequest = entitlementRequest(tenantId, applicationId);

    var expectedEntitlement = entitlement(tenantId, applicationId);
    getEntitlementsByQuery(queryByTenantAndAppId(tenantId, applicationId), entitlements(expectedEntitlement));

    mockMvc.perform(delete("/entitlements")
        .queryParam("tenantParameters", TENANT_PARAMETERS)
        .queryParam("purge", "false")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest)))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: FAILED")))
      .andExpect(jsonPath("$.errors[0].type", is("FlowExecutionException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("RevokeRequestDependencyValidator")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(
        "FAILED: [IllegalStateException] The following applications must be uninstalled first: [okapi-app3-3.0.0]")));

    getEntitlementsByQuery(queryByTenantAndAppId(tenantId, applicationId), entitlements(expectedEntitlement));
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/okapi-app/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/okapi-app/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/okapi/_proxy/install-okapi-module-not-found.json"
  })
  void install_negative() throws Exception {
    mockMvc.perform(post("/entitlements")
        .queryParam("ignoreErrors", "false")
        .queryParam("purgeOnRollback", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(OKAPI_APP_ID))))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: CANCELLED")))
      .andExpect(jsonPath("$.errors[0].type", is("FlowCancelledException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("OkapiModulesInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", startsWith(
        "FAILED: [BadRequest] [400 Bad Request] during [POST] to")));

    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null)).isEmpty();
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).isEmpty();
    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_ID), emptyEntitlements());
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/okapi-app/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/okapi-app/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
  })
  void install_negative_kongGateway502() throws Exception {
    doAnswer(OkapiEntitlementIT::badGatewayErr).when(kongAdminClient).upsertRoute(any(), eq(INVALID_ROUTE_HASH), any());
    doAnswer(OkapiEntitlementIT::badGatewayErr).when(kongAdminClient).deleteRoute(any(), any());

    mockMvc.perform(post("/entitlements")
        .queryParam("ignoreErrors", "false")
        .queryParam("purgeOnRollback", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(OKAPI_APP_ID))))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: CANCELLATION_FAILED")))
      .andExpect(jsonPath("$.errors[0].type", is("FlowCancellationException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("KongRouteCreator")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", startsWith(
        "CANCELLATION_FAILED: [KongIntegrationException] Failed to remove routes, parameters:")));

    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null)).hasSize(2);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).isEmpty();
    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_ID), emptyEntitlements());
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/okapi-app/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/okapi-app/get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/okapi/_proxy/install-okapi-module-not-found.json"
  })
  void install_negative_withIgnoreErrorsEnabled() throws Exception {
    mockMvc.perform(post("/entitlements")
        .queryParam("ignoreErrors", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(OKAPI_APP_ID))))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: FAILED")))
      .andExpect(jsonPath("$.errors[0].type", is("FlowExecutionException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("OkapiModulesInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", startsWith(
        "FAILED: [BadRequest] [400 Bad Request] during [POST] to")));

    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null)).hasSize(3);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      authResource("/okapi-module/entities", "GET"), authResource("/okapi-module/entities/{id}", "PUT"));
    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_ID), emptyEntitlements());
  }

  @Test
  @Sql("/sql/okapi-app-installed.sql")
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/okapi-app/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/okapi-app/get-discovery.json",
    "/wiremock/okapi/_proxy/uninstall-okapi-module-not-found.json"
  })
  void uninstall_negative() throws Exception {
    mockMvc.perform(delete("/entitlements")
        .queryParam("purge", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(OKAPI_APP_ID))))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: FAILED")))
      .andExpect(jsonPath("$.errors[0].type", is("FlowExecutionException")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("OkapiModulesInstaller")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", startsWith(
        "FAILED: [BadRequest] [400 Bad Request] during [POST] to")));

    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null)).isEmpty();
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).isEmpty();
    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_ID), entitlements(entitlement(OKAPI_APP_ID)));
  }

  @Test
  void uninstall_negative_emptyApplications() throws Exception {
    var entitlementRequest = new EntitlementRequestBody().tenantId(TENANT_ID);
    mockMvc.perform(delete("/entitlements")
        .queryParam("purge", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest)))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.errors[0].message", is("size must be between 1 and 25")))
      .andExpect(jsonPath("$.errors[0].type", is("MethodArgumentNotValidException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  void uninstall_negative_applicationsNotArray() throws Exception {
    var entitlementRequest = Map.of("tenantId", TENANT_ID, "applications", "test-app-2.0.0");
    mockMvc.perform(delete("/entitlements")
        .queryParam("purge", TRUE.toString())
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest)))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.errors[0].message", containsString("Cannot construct instance of `java.util.ArrayList`")))
      .andExpect(jsonPath("$.errors[0].type", is("HttpMessageNotReadableException")))
      .andExpect(jsonPath("$.errors[0].code", is("validation_error")));
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/okapi-app15/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/okapi-app/get-discovery-not-found.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json"
  })
  void install_negative_dependentApplicationsInSingleCallAndQueuedAppFlowsRemoved() throws Exception {
    mockMvc.perform(post("/entitlements")
        .queryParam("ignoreErrors", "false")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest(TENANT_ID, OKAPI_APP_ID, OKAPI_APP_5_ID))))
      .andExpect(status().isBadRequest())
      .andExpect(content().contentType(APPLICATION_JSON))
      .andExpect(jsonPath("$.total_records", is(1)))
      .andExpect(jsonPath("$.errors[0].message", matchesPattern("Flow '.+' finished with status: CANCELLED")))
      .andExpect(jsonPath("$.errors[0].type", is("FlowCancelledException")))
      .andExpect(jsonPath("$.errors[0].code", is("service_error")))
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("ApplicationDiscoveryLoader")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", startsWith(
        "FAILED: [IntegrationException] Failed to retrieve module discovery descriptors: " + OKAPI_APP_ID)));

    var entitlementQuery = String.format("applicationId==(%s or %s)", OKAPI_APP_ID, OKAPI_APP_5_ID);
    assertEntitlementsWithModules(entitlementQuery, emptyEntitlements());
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).isEmpty();

    var mvcResult = mockMvc.perform(get("/application-flows")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN))
      .andExpect(status().isOk())
      .andReturn();

    var result = parseResponse(mvcResult, ApplicationFlows.class);
    assertThat(result.getApplicationFlows()).satisfies(
      appFlows -> assertThat(appFlows).hasSize(1),
      appFlows -> assertThat(appFlows.get(0).getStatus()).isEqualTo(ExecutionStatus.CANCELLED)
    );
  }

  @Test
  @KeycloakRealms("/keycloak/test-realm.json")
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app6/v1-get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app6/v2-get-by-ids-query-full.json",
    "/wiremock/mgr-applications/folio-app6/v1-get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/okapi/_proxy/install-module-for-upgrade-test.json"
  })
  void upgrade_positive_freshInstallation() throws Exception {
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");
    var applicationId = "folio-app6-6.0.0";
    var entitlementRequest = entitlementRequest(applicationId);
    entitleApplications(entitlementRequest, queryParams, extendedEntitlements(entitlement(applicationId)));

    assertThat(kongAdminClient.getRoutesByTag(FOLIO_MODULE2_ID, null)).hasSize(5);
    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null))
      .map(Route::getExpression)
      .containsExactlyInAnyOrder(
        regexRouteExpression("^/folio-module1/events/([^/]+)$", "GET"),
        regexRouteExpression("^/folio-module2/events/([^/]+)$", "GET"),
        exactRouteExpression("/folio-module2/events", "GET"),
        exactRouteExpression("/folio-module1/events", "POST"),
        exactRouteExpression("/folio-module2/events", "POST"),
        exactRouteExpression("/folio-module1/events", "GET"),
        exactRouteExpression("/folio-module1/v1/scheduled-timer", "POST"),
        exactRouteExpression("/folio-module2/v1/scheduled-timer1", "POST"),
        exactRouteExpression("/folio-module2/v1/scheduled-timer2", "POST"));

    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      authResource("/folio-module1/events", "GET", "POST"),
      authResource("/folio-module1/events/{id}", "GET"),
      authResource("/folio-module2/events", "GET", "POST"),
      authResource("/folio-module2/events/{id}", "GET"));

    assertScheduledJobEvents(
      readScheduledJobEvent("json/events/folio-app6/folio-module1/timer-event.json"),
      readScheduledJobEvent("json/events/folio-app6/folio-module2/timer-event.json"));

    assertCapabilityEvents(
      readCapabilityEvent("json/events/folio-app6/folio-module1/capability-event.json"),
      readCapabilityEvent("json/events/folio-app6/folio-module2/capability-event.json"));

    var applicationIdV2 = "folio-app6-6.1.0";
    var upgradeRequest = entitlementRequest(applicationIdV2);
    upgradeApplications(upgradeRequest, queryParams, extendedEntitlements(entitlement(applicationIdV2)));
    getEntitlementsByQuery("applicationId == " + applicationIdV2, entitlements(entitlement(applicationIdV2)));
    getEntitlementsByQuery("applicationId == " + applicationId, emptyEntitlements());

    assertThat(kongAdminClient.getRoutesByTag(FOLIO_MODULE2_ID, null)).isEmpty();
    assertThat(kongAdminClient.getRoutesByTag(FOLIO_MODULE2_V2_ID, null)).hasSize(6);
    assertThat(kongAdminClient.getRoutesByTag(TENANT_NAME, null))
      .map(Route::getExpression)
      .containsExactlyInAnyOrder(
        regexRouteExpression("^/folio-module1/events/([^/]+)$", "GET"),
        regexRouteExpression("^/folio-module2/events/([^/]+)$", "GET"),
        exactRouteExpression("/folio-module2/v2/events", "POST"),
        exactRouteExpression("/folio-module2/events", "PUT"),
        exactRouteExpression("/folio-module1/events", "GET"),
        exactRouteExpression("/folio-module1/events", "POST"),
        exactRouteExpression("/folio-module2/events", "GET"),
        exactRouteExpression("/folio-module1/v1/scheduled-timer", "POST"),
        exactRouteExpression("/folio-module2/v2/scheduled-timer1", "POST"),
        exactRouteExpression("/folio-module2/v1/scheduled-timer2", "POST"));

    assertThat(keycloakTestClient.getAuthorizationScopes(TENANT_NAME)).containsExactlyElementsOf(ALL_HTTP_METHODS);
    assertThat(keycloakTestClient.getAuthorizationResources(TENANT_NAME)).containsExactly(
      authResource("/folio-module1/events", "GET", "POST"),
      authResource("/folio-module1/events/{id}", "GET"),
      authResource("/folio-module2/events", "GET", "PUT"),
      authResource("/folio-module2/events/{id}", "GET"),
      authResource("/folio-module2/v2/events", "POST"));

    assertScheduledJobEvents(readScheduledJobEvent("json/events/folio-app6/folio-module2/timer-update-event.json"));
    assertCapabilityEvents(readCapabilityEvent("json/events/folio-app6/folio-module2/capability-update-event.json"));
  }

  @SneakyThrows
  private static Object badGatewayErr(InvocationOnMock invocation) {
    var routeId = invocation.<String>getArgument(1);
    var serviceId = invocation.<String>getArgument(0);
    var url = String.format("/services/%s/routes/%s", serviceId, routeId);
    var request = Request.create(HttpMethod.PUT, url, emptyMap(), null, UTF_8, null);
    throw new BadGateway("502 Bad Gateway", request, null, emptyMap());
  }

  private static void checkApplicationContextBeans(ApplicationContext appContext) {
    checkExistingBeans(appContext, OKAPI_MODULE_INSTALLER_BEAN_TYPES);
    checkExistingBeans(appContext, COMMON_KONG_INTEGRATION_BEAN_TYPES);
    checkExistingBeans(appContext, OKAPI_KONG_INTEGRATION_BEAN_TYPES);
    checkExistingBeans(appContext, COMMON_KEYCLOAK_INTEGRATION_BEAN_TYPES);
    checkExistingBeans(appContext, OKAPI_KEYCLOAK_INTEGRATION_BEAN_TYPES);

    checkMissingBeans(appContext, FOLIO_KONG_INTEGRATION_BEAN_TYPES);
    checkMissingBeans(appContext, FOLIO_KEYCLOAK_INTEGRATION_BEAN_TYPES);
    checkMissingBeans(appContext, FOLIO_MODULE_INSTALLER_BEAN_TYPES);
  }
}
