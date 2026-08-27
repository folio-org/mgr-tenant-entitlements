package org.folio.entitlement.it;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.capabilitiesTenantTopic;
import static org.folio.entitlement.support.TestConstants.scheduledJobsTenantTopic;
import static org.folio.entitlement.support.TestConstants.systemUserTenantTopic;
import static org.folio.entitlement.support.TestUtils.parseResponse;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.folio.integration.kafka.model.ResourceResultStatus.FAILURE;
import static org.folio.integration.kafka.model.ResourceResultStatus.SUCCESS;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.awaitility.Awaitility;
import org.folio.entitlement.domain.dto.ExtendedEntitlements;
import org.folio.entitlement.integration.kafka.KafkaEventUtils;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.integration.kafka.model.ResourceEvent;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.integration.kafka.model.ResourceResultStatus;
import org.folio.test.FakeKafkaConsumer;
import org.folio.test.TestConstants;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.web.servlet.ResultActions;

@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
@TestPropertySource(properties = {
  "application.environment=folio",
  "application.apigw.enabled=false",
  "application.keycloak.enabled=false",
  "application.event-publishing.capability.await-completion=true",
  "application.event-publishing.system-user.await-completion=true",
  "application.event-publishing.scheduled-job.await-completion=true",
})
class AsyncConfirmationIT extends BaseIntegrationTest {

  private static final String TENANT_NAME = "test";
  private static final String RESULT_TOPIC = "folio.mgr-tenant-entitlements.resource-result";
  private static final String FOLIO_APP_ASYNC_1_ID = "folio-app-async-1.0.0";
  private static final String FOLIO_APP_ASYNC_2_ID = "folio-app-async-2.0.0";

  private static final String CAP_STAGE = "folio-module-async-1.0.0-capabilitiesModuleEventPublisher";
  private static final String SCHED_STAGE = "folio-module-async-1.0.0-scheduledJobModuleEventPublisher";
  private static final String SYS_USER_STAGE = "folio-module-async-1.0.0-systemUserModuleEventPublisher";

  private static final String CAPABILITY_RESOURCE_NAME = KafkaEventUtils.CAPABILITY_RESOURCE_NAME;
  private static final String SCHEDULED_JOB_RESOURCE_NAME = KafkaEventUtils.SCHEDULED_JOB_RESOURCE_NAME;
  private static final String SYSTEM_USER_RESOURCE_NAME = KafkaEventUtils.SYSTEM_USER_RESOURCE_NAME;

  private static final Map<String, String> QUERY_PARAMS = Map.of("tenantParameters", "loadReference=true");

  @Autowired
  private KafkaTemplate<String, Object> kafkaTemplate;

  @BeforeAll
  static void setUpTopics() {
    fakeKafkaConsumer.registerTopic(capabilitiesTenantTopic(), ResourceEvent.class);
    fakeKafkaConsumer.registerTopic(scheduledJobsTenantTopic(), ResourceEvent.class);
    fakeKafkaConsumer.registerTopic(systemUserTenantTopic(), ResourceEvent.class);
  }

  @BeforeEach
  void clearEvents() {
    FakeKafkaConsumer.removeAllEvents();
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app-async/v1-get-by-ids-query-app1.json",
    "/wiremock/mgr-applications/folio-app-async/v1-get-discovery-app1.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module-async/install.json"
  })
  void entitle_positive_singleApp_allStagesSucceed() throws Exception {
    var mvcResult = entitleApplications(entitlementRequest(FOLIO_APP_ASYNC_1_ID), QUERY_PARAMS,
      extendedEntitlements(entitlement(FOLIO_APP_ASYNC_1_ID)));
    final var flowId = parseResponse(mvcResult, ExtendedEntitlements.class).getFlowId();

    var capStageId = awaitStageId(capabilitiesTenantTopic(), 0);
    var schedStageId = awaitStageId(scheduledJobsTenantTopic(), 0);
    var sysUserStageId = awaitStageId(systemUserTenantTopic(), 0);

    sendResult(capStageId, SUCCESS, CAPABILITY_RESOURCE_NAME, null);
    sendResult(schedStageId, SUCCESS, SCHEDULED_JOB_RESOURCE_NAME, null);
    sendResult(sysUserStageId, SUCCESS, SYSTEM_USER_RESOURCE_NAME, null);

    Awaitility.await().atMost(30, SECONDS).untilAsserted(() ->
      getFlow(flowId, true)
        .andExpect(jsonPath("$.status", is("finished")))
        .andExpect(jsonPath("$.applicationFlows[0].status", is("finished")))
        .andExpect(jsonPath("$.applicationFlows[0].stages[?(@.name == '" + CAP_STAGE + "')].status",
          contains("finished")))
        .andExpect(jsonPath("$.applicationFlows[0].stages[?(@.name == '" + SCHED_STAGE + "')].status",
          contains("finished")))
        .andExpect(jsonPath("$.applicationFlows[0].stages[?(@.name == '" + SYS_USER_STAGE + "')].status",
          contains("finished")))
    );
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app-async/v1-get-by-ids-query-app1.json",
    "/wiremock/mgr-applications/folio-app-async/v1-get-discovery-app1.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module-async/install.json"
  })
  void entitle_positive_singleApp_partialSuccess_flowStaysInProgress() throws Exception {
    var mvcResult = entitleApplications(entitlementRequest(FOLIO_APP_ASYNC_1_ID), QUERY_PARAMS,
      extendedEntitlements(entitlement(FOLIO_APP_ASYNC_1_ID)));
    final var flowId = parseResponse(mvcResult, ExtendedEntitlements.class).getFlowId();

    var capStageId = awaitStageId(capabilitiesTenantTopic(), 0);
    awaitStageId(scheduledJobsTenantTopic(), 0);
    awaitStageId(systemUserTenantTopic(), 0);

    sendResult(capStageId, SUCCESS, CAPABILITY_RESOURCE_NAME, null);

    Awaitility.await()
      .during(3, SECONDS)
      .atMost(5, SECONDS)
      .untilAsserted(() ->
        getFlow(flowId, true)
          .andExpect(jsonPath("$.status", is("in_progress")))
          .andExpect(jsonPath("$.applicationFlows[0].stages[?(@.name == '" + CAP_STAGE + "')].status",
            contains("finished")))
          .andExpect(jsonPath("$.applicationFlows[0].stages[?(@.name == '" + SCHED_STAGE + "')].status",
            contains("in_progress")))
          .andExpect(jsonPath("$.applicationFlows[0].stages[?(@.name == '" + SYS_USER_STAGE + "')].status",
            contains("in_progress")))
      );
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app-async/v1-get-by-ids-query-app1.json",
    "/wiremock/mgr-applications/folio-app-async/v1-get-discovery-app1.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module-async/install.json"
  })
  void entitle_positive_singleApp_failureWithDetails() throws Exception {
    var mvcResult = entitleApplications(entitlementRequest(FOLIO_APP_ASYNC_1_ID), QUERY_PARAMS,
      extendedEntitlements(entitlement(FOLIO_APP_ASYNC_1_ID)));
    var flowId = parseResponse(mvcResult, ExtendedEntitlements.class).getFlowId();

    var capStageId = awaitStageId(capabilitiesTenantTopic(), 0);

    sendResult(capStageId, FAILURE, CAPABILITY_RESOURCE_NAME, "downstream-error");

    Awaitility.await().atMost(30, SECONDS).untilAsserted(() ->
      getFlow(flowId, true)
        .andExpect(jsonPath("$.status", is("failed")))
        .andExpect(jsonPath("$.applicationFlows[0].status", is("failed")))
        .andExpect(jsonPath("$.applicationFlows[0].stages[?(@.name == '" + CAP_STAGE + "')].status",
          contains("failed")))
        .andExpect(jsonPath("$.applicationFlows[0].stages[?(@.name == '" + CAP_STAGE + "')].errorMessage",
          contains("downstream-error")))
    );
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app-async/v1-get-by-ids-query-app1.json",
    "/wiremock/mgr-applications/folio-app-async/v1-get-discovery-app1.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module-async/install.json"
  })
  void entitle_positive_singleApp_failureWithNullDetails_errorMessagePopulated() throws Exception {
    var mvcResult = entitleApplications(entitlementRequest(FOLIO_APP_ASYNC_1_ID), QUERY_PARAMS,
      extendedEntitlements(entitlement(FOLIO_APP_ASYNC_1_ID)));
    var flowId = parseResponse(mvcResult, ExtendedEntitlements.class).getFlowId();

    var capStageId = awaitStageId(capabilitiesTenantTopic(), 0);

    sendResult(capStageId, FAILURE, CAPABILITY_RESOURCE_NAME, null);

    Awaitility.await().atMost(30, SECONDS).untilAsserted(() ->
      getFlow(flowId, true)
        .andExpect(jsonPath("$.status", is("failed")))
        .andExpect(jsonPath("$.applicationFlows[0].stages[?(@.name == '" + CAP_STAGE + "')].status",
          contains("failed")))
        .andExpect(jsonPath(
          "$.applicationFlows[0].stages[?(@.name == '" + CAP_STAGE + "')].errorMessage",
          contains("Downstream service reported a failure with no details")))
    );
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app-async/v1-get-by-ids-query-both.json",
    "/wiremock/mgr-applications/folio-app-async/v1-get-discovery-app1.json",
    "/wiremock/mgr-applications/folio-app-async/v1-get-discovery-app2.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module-async/install.json",
    "/wiremock/folio-module-async-2/install.json"
  })
  void entitle_positive_twoApps_firstCompletes_secondStillPending() throws Exception {
    var mvcResult = entitleApplications(
      entitlementRequest(TENANT_ID, FOLIO_APP_ASYNC_1_ID, FOLIO_APP_ASYNC_2_ID), QUERY_PARAMS,
      extendedEntitlements(entitlement(FOLIO_APP_ASYNC_1_ID), entitlement(FOLIO_APP_ASYNC_2_ID)));
    final var flowId = parseResponse(mvcResult, ExtendedEntitlements.class).getFlowId();

    Awaitility.await().atMost(10, SECONDS)
      .until(() -> FakeKafkaConsumer.getEvents(capabilitiesTenantTopic(), ResourceEvent.class).size() >= 2);
    var schedStageId = awaitStageId(scheduledJobsTenantTopic(), 0);
    var sysUserStageId = awaitStageId(systemUserTenantTopic(), 0);

    var capEvents = FakeKafkaConsumer.getEvents(capabilitiesTenantTopic(), ResourceEvent.class);
    var app1CapId = capEvents.stream()
      .filter(r -> FOLIO_APP_ASYNC_1_ID.equals(appIdFrom(r.value().getNewValue())))
      .map(r -> UUID.fromString(r.value().getId()))
      .findFirst()
      .orElseThrow();

    sendResult(app1CapId, SUCCESS, CAPABILITY_RESOURCE_NAME, null);
    sendResult(schedStageId, SUCCESS, SCHEDULED_JOB_RESOURCE_NAME, null);
    sendResult(sysUserStageId, SUCCESS, SYSTEM_USER_RESOURCE_NAME, null);

    Awaitility.await()
      .during(3, SECONDS)
      .atMost(5, SECONDS)
      .untilAsserted(() ->
        getFlow(flowId, false)
          .andExpect(jsonPath("$.status", is("in_progress")))
          .andExpect(jsonPath(
            "$.applicationFlows[?(@.applicationId == '" + FOLIO_APP_ASYNC_1_ID + "')].status",
            contains("finished")))
          .andExpect(jsonPath(
            "$.applicationFlows[?(@.applicationId == '" + FOLIO_APP_ASYNC_2_ID + "')].status",
            contains("in_progress")))
      );
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/folio-app-async/v1-get-by-ids-query-both.json",
    "/wiremock/mgr-applications/folio-app-async/v1-get-discovery-app1.json",
    "/wiremock/mgr-applications/folio-app-async/v1-get-discovery-app2.json",
    "/wiremock/mgr-applications/validate-any-descriptor.json",
    "/wiremock/folio-module-async/install.json",
    "/wiremock/folio-module-async-2/install.json"
  })
  void entitle_positive_twoApps_allStagesSucceed_topLevelFlowFinishes() throws Exception {
    var mvcResult = entitleApplications(
      entitlementRequest(TENANT_ID, FOLIO_APP_ASYNC_1_ID, FOLIO_APP_ASYNC_2_ID), QUERY_PARAMS,
      extendedEntitlements(entitlement(FOLIO_APP_ASYNC_1_ID), entitlement(FOLIO_APP_ASYNC_2_ID)));
    final var flowId = parseResponse(mvcResult, ExtendedEntitlements.class).getFlowId();

    Awaitility.await().atMost(10, SECONDS)
      .until(() -> FakeKafkaConsumer.getEvents(capabilitiesTenantTopic(), ResourceEvent.class).size() >= 2);
    var schedStageId = awaitStageId(scheduledJobsTenantTopic(), 0);
    final var sysUserStageId = awaitStageId(systemUserTenantTopic(), 0);

    var capEvents = FakeKafkaConsumer.getEvents(capabilitiesTenantTopic(), ResourceEvent.class);
    var app1CapId = capEvents.stream()
      .filter(r -> FOLIO_APP_ASYNC_1_ID.equals(appIdFrom(r.value().getNewValue())))
      .map(r -> UUID.fromString(r.value().getId()))
      .findFirst()
      .orElseThrow();
    var app2CapId = capEvents.stream()
      .filter(r -> FOLIO_APP_ASYNC_2_ID.equals(appIdFrom(r.value().getNewValue())))
      .map(r -> UUID.fromString(r.value().getId()))
      .findFirst()
      .orElseThrow();

    sendResult(app1CapId, SUCCESS, CAPABILITY_RESOURCE_NAME, null);
    sendResult(app2CapId, SUCCESS, CAPABILITY_RESOURCE_NAME, null);
    sendResult(schedStageId, SUCCESS, SCHEDULED_JOB_RESOURCE_NAME, null);
    sendResult(sysUserStageId, SUCCESS, SYSTEM_USER_RESOURCE_NAME, null);

    Awaitility.await().atMost(30, SECONDS).untilAsserted(() ->
      getFlow(flowId, false)
        .andExpect(jsonPath("$.status", is("finished")))
        .andExpect(jsonPath(
          "$.applicationFlows[?(@.applicationId == '" + FOLIO_APP_ASYNC_1_ID + "')].status",
          contains("finished")))
        .andExpect(jsonPath(
          "$.applicationFlows[?(@.applicationId == '" + FOLIO_APP_ASYNC_2_ID + "')].status",
          contains("finished")))
    );
  }

  private UUID awaitStageId(String topic, int index) {
    Awaitility.await().atMost(10, SECONDS)
      .until(() -> FakeKafkaConsumer.getEvents(topic, ResourceEvent.class).size() > index);
    return UUID.fromString(
      FakeKafkaConsumer.getEvents(topic, ResourceEvent.class).get(index).value().getId());
  }

  private void sendResult(UUID stageId, ResourceResultStatus status, String resourceName, String details) {
    kafkaTemplate.send(RESULT_TOPIC, TENANT_NAME, ResourceResultEvent.builder()
      .id(stageId.toString())
      .tenant(TENANT_NAME)
      .status(status)
      .resourceName(resourceName)
      .details(details)
      .build());
  }

  private static ResultActions getFlow(UUID flowId, boolean includeStages) throws Exception {
    return mockMvc.perform(get("/entitlement-flows/{flowId}", flowId)
        .header(TOKEN, TestConstants.OKAPI_AUTH_TOKEN)
        .queryParam("includeStages", String.valueOf(includeStages)))
      .andExpect(status().isOk());
  }

  @SuppressWarnings("unchecked")
  private static String appIdFrom(Object newValue) {
    if (newValue instanceof Map<?, ?> map) {
      return (String) map.get("applicationId");
    }
    return null;
  }
}
