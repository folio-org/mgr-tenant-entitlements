package org.folio.entitlement.it;

import static java.lang.Boolean.TRUE;
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
import static org.folio.entitlement.support.UpgradeTestValues.FOLIO_APP6_V1_ID;
import static org.folio.entitlement.support.UpgradeTestValues.FOLIO_APP6_V2_ID;
import static org.folio.entitlement.support.UpgradeTestValues.capabilityEventsAfterUpgrade;
import static org.folio.entitlement.support.UpgradeTestValues.capabilityEventsBeforeUpgrade;
import static org.folio.entitlement.support.UpgradeTestValues.scheduledTimerEventsAfterUpgrade;
import static org.folio.entitlement.support.UpgradeTestValues.scheduledTimerEventsBeforeUpgrade;
import static org.folio.entitlement.support.UpgradeTestValues.systemUserEventsAfterUpgrade;
import static org.folio.entitlement.support.UpgradeTestValues.systemUserEventsBeforeUpgrade;
import static org.folio.test.TestConstants.OKAPI_AUTH_TOKEN;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.startsWith;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.entitlement.domain.dto.ApplicationFlows;
import org.folio.entitlement.domain.dto.EntitlementRequestBody;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.ExtendedEntitlements;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.EnableOkapiSecurity;
import org.folio.test.extensions.KeycloakRealms;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

@IntegrationTest
@EnableOkapiSecurity
@SqlMergeMode(MERGE)
@TestPropertySource(properties = {
  "application.keycloak.enabled=false",
  "application.okapi.enabled=true",
  "application.kong.enabled=false",
})
@WireMockStub("/wiremock/mod-authtoken/verify-token-any.json")
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "/sql/truncate-tables.sql")
class NoIntegrationsOkapiEntitlementIT extends BaseIntegrationTest {

  private static final String OKAPI_APP_ID = "okapi-app-1.0.0";
  private static final String OKAPI_APP_3_ID = "okapi-app3-3.0.0";
  private static final String OKAPI_APP_4_ID = "okapi-app4-4.0.0";
  private static final String OKAPI_APP_5_ID = "okapi-app5-5.0.0";
  private static final String OKAPI_MODULE_ID = "okapi-module-1.0.0";
  private static final String OKAPI_MODULE_3_ID = "okapi-module3-1.0.0";
  private static final String OKAPI_MODULE_4_ID = "okapi-module4-4.0.0";
  private static final String OKAPI_MODULE_5_ID = "okapi-module5-5.0.0";

  @BeforeAll
  static void beforeAll(@Autowired ApplicationContext appContext) {
    checkApplicationContextBeans(appContext);
    fakeKafkaConsumer.registerTopic(entitlementTopic(), EntitlementEvent.class);
    fakeKafkaConsumer.registerTopic(capabilitiesTenantTopic(), ResourceEvent.class);
    fakeKafkaConsumer.registerTopic(scheduledJobsTenantTopic(), ResourceEvent.class);
    fakeKafkaConsumer.registerTopic(systemUserTenantTopic(), ResourceEvent.class);
  }

  @Test
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
  @WireMockStub(scripts = {
    "/wiremock/mod-authtoken/verify-token-install.json",
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

    // entitle dependent application
    var request = entitlementRequest(OKAPI_APP_5_ID);
    entitleApplications(request, emptyMap(), extendedEntitlements(entitlement(OKAPI_APP_5_ID)));
    var entitlement2 = entitlementWithModules(TENANT_ID, OKAPI_APP_5_ID, List.of(OKAPI_MODULE_5_ID));
    assertEntitlementsWithModules(queryByTenantAndAppId(OKAPI_APP_5_ID), entitlements(entitlement2));

    var savedEntitlementQuery = String.format("applicationId==(%s or %s)", OKAPI_APP_ID, OKAPI_APP_5_ID);
    assertEntitlementsWithModules(savedEntitlementQuery, entitlements(entitlement1, entitlement2));

    assertEntitlementEvents(entitlementEvent(ENTITLE, OKAPI_MODULE_ID), entitlementEvent(ENTITLE, OKAPI_MODULE_5_ID));
    assertCapabilityEvents(
      readCapabilityEvent("json/events/okapi-it/okapi-module-capability-event-1.json"),
      readCapabilityEvent("json/events/okapi-it/okapi-module-capability-event-2.json"));
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mod-authtoken/verify-token-install.json",
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
    assertEntitlementEvents(entitlementEvent(ENTITLE, OKAPI_MODULE_ID), entitlementEvent(ENTITLE, OKAPI_MODULE_5_ID));

    assertCapabilityEvents(
      readCapabilityEvent("json/events/okapi-it/okapi-module-capability-event-1.json"),
      readCapabilityEvent("json/events/okapi-it/okapi-module-capability-event-2.json"));
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mod-authtoken/verify-token-install.json",
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
    assertEntitlementEvents(entitlementEvent(ENTITLE, OKAPI_MODULE_ID));
    assertCapabilityEvents(readCapabilityEvent("json/events/okapi-it/okapi-module-capability-event-1.json"));
    assertScheduledJobEvents(readScheduledJobEvent("json/events/okapi-it/okapi-module1/scheduled-job-event.json"));
    assertSystemUserEvents(readSystemUserEvent("json/events/okapi-it/system-user-event.json"));
  }

  @Test
  @Sql("/sql/okapi-app-installed.sql")
  @WireMockStub(scripts = {
    "/wiremock/mod-authtoken/verify-token-uninstall.json",
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
    assertEntitlementsWithModules(queryByTenantAndAppId(OKAPI_APP_ID), emptyEntitlements());
  }

  @Test
  @Sql("/sql/okapi-app3-installed.sql")
  @WireMockStub(scripts = {
    "/wiremock/mod-authtoken/verify-token-uninstall.json",
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

    var entitlementQuery = String.format("applicationId==(%s or %s)", OKAPI_APP_3_ID, OKAPI_APP_4_ID);
    assertEntitlementsWithModules(entitlementQuery, emptyEntitlements());
  }

  @Test
  @Sql("/sql/okapi-app-revoked.sql")
  @WireMockStub(scripts = {
    "/wiremock/mod-authtoken/verify-token-uninstall.json",
    "/wiremock/mgr-tenants/test/get.json"})
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
  @WireMockStub(scripts = {
    "/wiremock/mod-authtoken/verify-token-install.json",
    "/wiremock/mod-authtoken/verify-token-uninstall.json",
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
    assertEntitlementEvents(entitlementEvent(ENTITLE, OKAPI_MODULE_ID));

    // entitle application
    var entitlementRequest = entitlementRequest(OKAPI_APP_5_ID);
    entitleApplications(entitlementRequest, queryParams, extendedEntitlements(entitlement(OKAPI_APP_5_ID)));
    assertEntitlementEvents(entitlementEvent(ENTITLE, OKAPI_MODULE_ID), entitlementEvent(ENTITLE, OKAPI_MODULE_5_ID));

    // revoke entitlement for test application
    var revokeParams = Map.of("purge", "true");
    revokeEntitlements(entitlementRequest, revokeParams, extendedEntitlements(entitlement(OKAPI_APP_5_ID)));
    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_5_ID), emptyEntitlements());
    assertEntitlementEvents(
      entitlementEvent(ENTITLE, OKAPI_MODULE_ID), entitlementEvent(ENTITLE, OKAPI_MODULE_5_ID),
      entitlementEvent(REVOKE, OKAPI_MODULE_5_ID));

    // entitle test application again
    entitleApplications(entitlementRequest, queryParams, extendedEntitlements(entitlement(OKAPI_APP_5_ID)));
    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_5_ID), entitlements(entitlement(OKAPI_APP_5_ID)));
    assertEntitlementEvents(
      entitlementEvent(ENTITLE, OKAPI_MODULE_ID), entitlementEvent(ENTITLE, OKAPI_MODULE_5_ID),
      entitlementEvent(REVOKE, OKAPI_MODULE_5_ID), entitlementEvent(ENTITLE, OKAPI_MODULE_5_ID));
    assertCapabilityEvents(readCapabilityEvent("json/events/okapi-it/okapi-module-capability-event-1.json"));
  }

  @Test
  @WireMockStub("/wiremock/mod-authtoken/verify-token-install.json")
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
  @WireMockStub("/wiremock/mod-authtoken/verify-token-install.json")
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
  @WireMockStub("/wiremock/mod-authtoken/verify-token-install.json")
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
  @WireMockStub("/wiremock/mod-authtoken/verify-token-install.json")
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
    "/wiremock/mod-authtoken/verify-token-uninstall.json",
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

    getEntitlementsByQuery(
      queryByTenantAndAppId(tenantId, applicationId), entitlements(expectedEntitlement));
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mod-authtoken/verify-token-install.json",
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

    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_ID), emptyEntitlements());
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mod-authtoken/verify-token-install.json",
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

    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_ID), emptyEntitlements());
  }

  @Test
  @Sql("/sql/okapi-app-installed.sql")
  @WireMockStub(scripts = {
    "/wiremock/mod-authtoken/verify-token-uninstall.json",
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

    getEntitlementsByQuery(queryByTenantAndAppId(OKAPI_APP_ID), entitlements(entitlement(OKAPI_APP_ID)));
  }

  @Test
  @WireMockStub("/wiremock/mod-authtoken/verify-token-uninstall.json")
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
  @WireMockStub("/wiremock/mod-authtoken/verify-token-install-not-authorized.json")
  void install_negative_unauthorized() throws Exception {
    var entitlementRequest = new EntitlementRequestBody().tenantId(TENANT_ID);

    mockMvc.perform(post("/entitlements")
        .queryParam("tenantParameters", TENANT_PARAMETERS)
        .queryParam("ignoreErrors", "true")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest)))
      .andExpect(status().isUnauthorized());
  }

  @Test
  @WireMockStub("/wiremock/mod-authtoken/verify-token-install-permission-denied.json")
  void install_negative_forbidden() throws Exception {
    var entitlementRequest = new EntitlementRequestBody().tenantId(TENANT_ID);

    mockMvc.perform(post("/entitlements")
        .queryParam("tenantParameters", TENANT_PARAMETERS)
        .queryParam("ignoreErrors", String.valueOf(IGNORE_ERRORS))
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .content(asJsonString(entitlementRequest)))
      .andExpect(status().isForbidden());
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mod-authtoken/verify-token-install.json",
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/okapi-app15/get-by-ids-query-full.json",
    "/wiremock/mgr-applications/okapi-app/get-discovery-not-found.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json"
  })
  void install_negative_dependentApplicationsInSingleCall_queuedAppFlowsRemoved() throws Exception {
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
    "/wiremock/mgr-applications/folio-app6/v1-get-discovery.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/okapi/_proxy/install-module-for-upgrade-test.json",

    "/wiremock/mgr-applications/folio-app6/v2-get-discovery.json",
    "/wiremock/mgr-applications/folio-app6/v2-get-by-ids-query-full.json",
  })
  void upgrade_positive_freshInstallation() throws Exception {
    var queryParams = Map.of("tenantParameters", "loadReference=true", "ignoreErrors", "true");

    var entitlementRequest = entitlementRequest(FOLIO_APP6_V1_ID);
    entitleApplications(entitlementRequest, queryParams, extendedEntitlements(entitlement(FOLIO_APP6_V1_ID)));

    assertScheduledJobEvents(scheduledTimerEventsBeforeUpgrade());
    assertCapabilityEvents(capabilityEventsBeforeUpgrade());
    assertSystemUserEvents(systemUserEventsBeforeUpgrade());

    var upgradeRequest = entitlementRequest(FOLIO_APP6_V2_ID);
    upgradeApplications(upgradeRequest, queryParams, extendedEntitlements(entitlement(FOLIO_APP6_V2_ID)));

    getEntitlementsByQuery("applicationId == " + FOLIO_APP6_V1_ID, emptyEntitlements());
    getEntitlementsByQuery("applicationId == " + FOLIO_APP6_V2_ID, entitlements(entitlement(FOLIO_APP6_V2_ID)));

    assertScheduledJobEvents(scheduledTimerEventsAfterUpgrade());
    assertCapabilityEvents(capabilityEventsAfterUpgrade());
    assertSystemUserEvents(systemUserEventsAfterUpgrade());

    mockMvc.perform(get("/entitlements")
        .contentType(APPLICATION_JSON)
        .header(TOKEN, OKAPI_AUTH_TOKEN)
        .queryParam("query", "applicationId == " + FOLIO_APP6_V1_ID))
      .andExpect(status().isOk())
      .andExpect(content().json(asJsonString(emptyEntitlements())));
  }

  @Test
  @Sql("/sql/okapi-app-upgraded.sql")
  void install_negative_upgradeIsFinishedForHigherVersion() throws Exception {
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
      .andExpect(jsonPath("$.errors[0].parameters[0].key", is("ApplicationFlowValidator")))
      .andExpect(jsonPath("$.errors[0].parameters[0].value", is(
        "FAILED: [RequestValidationException] Found validation errors in entitlement request, "
          + "parameters: [{key: okapi-app-1.1.0, value: Upgrade flow finished}]")));
  }

  private static void checkApplicationContextBeans(ApplicationContext appContext) {
    checkExistingBeans(appContext, OKAPI_MODULE_INSTALLER_BEAN_TYPES);

    checkMissingBeans(appContext, COMMON_KONG_INTEGRATION_BEAN_TYPES);
    checkMissingBeans(appContext, FOLIO_KONG_INTEGRATION_BEAN_TYPES);
    checkMissingBeans(appContext, OKAPI_KONG_INTEGRATION_BEAN_TYPES);
    checkMissingBeans(appContext, COMMON_KEYCLOAK_INTEGRATION_BEAN_TYPES);
    checkMissingBeans(appContext, FOLIO_KEYCLOAK_INTEGRATION_BEAN_TYPES);
    checkMissingBeans(appContext, OKAPI_KEYCLOAK_INTEGRATION_BEAN_TYPES);
    checkMissingBeans(appContext, FOLIO_MODULE_INSTALLER_BEAN_TYPES);
  }
}
