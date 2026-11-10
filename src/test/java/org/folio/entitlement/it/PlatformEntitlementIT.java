package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.folio.entitlement.controller.ApiExceptionHandler.FLOW_ID_HEADER;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestUtils.parseResponse;
import static org.folio.entitlement.support.TestValues.desiredStateRequest;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.ExtendedPlatformEntitlements;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.dto.Flows;
import org.folio.entitlement.domain.dto.PlatformEntitlementRequestBody;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.repository.PlatformApplicationDependencyRepository;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.entitlement.service.lock.PlatformLockService;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.extensions.WireMockStub;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
@TestPropertySource(properties = {
  "application.apigw.enabled=false",
  "application.keycloak.enabled=false"
})
class PlatformEntitlementIT extends BaseIntegrationTest {

  private static final String PLATFORM_PATH = "/entitlements/platform";
  private static final String PLATFORM_APP_V1 = "platform-app-1.0.0";
  private static final String PLATFORM_APP_V2 = "platform-app-2.0.0";
  private static final String PLATFORM_BASE_V1 = "platform-base-1.0.0";
  private static final String PLATFORM_APP2_V1 = "platform-app2-1.0.0";
  private static final String PLATFORM_APP2_V2 = "platform-app2-2.0.0";
  private static final String REGULAR_APP = "folio-app1-1.0.0";
  private static final String RETRY_AFTER = "Retry-After";
  private static final String DEFAULT_RETRY_AFTER_SECONDS = "5";

  private static final String STUB_VALIDATE_DESCRIPTOR = "/wiremock/mgr-applications/validate-any-descriptor.json";
  private static final String STUB_ENTITLED_PLATFORM_APP_V1 =
    "/wiremock/mgr-applications/platform/get-platform-app-v1.json";
  private static final String STUB_PLATFORM_APP =
    "/wiremock/mgr-applications/platform/get-by-ids-query-platform-app-v1.json";
  private static final String STUB_PLATFORM_APP_V2 =
    "/wiremock/mgr-applications/platform/get-by-ids-query-platform-app-v2.json";
  private static final String STUB_PLATFORM_APP2_DEPENDENCY_ENTITLED =
    "/wiremock/mgr-applications/platform/get-by-ids-query-platform-app2-dependency-entitled.json";
  private static final String STUB_PLATFORM_APPS_DEPENDENCY_ORDER =
    "/wiremock/mgr-applications/platform/get-by-ids-query-platform-apps-dependency-order.json";
  private static final String STUB_ENTITLED_PLATFORM_APP2_V1 =
    "/wiremock/mgr-applications/platform/get-platform-app2-v1.json";
  private static final String STUB_PLATFORM_APPS_ENTITLE_UNSATISFIED_DEPENDENCY =
    "/wiremock/mgr-applications/platform/get-by-ids-query-platform-apps-entitle-unsatisfied-dependency.json";
  private static final String STUB_PLATFORM_APPS_V2_UNSATISFIED_DEPENDENCY =
    "/wiremock/mgr-applications/platform/get-by-ids-query-platform-apps-v2-unsatisfied-dependency.json";
  private static final String STUB_REGULAR ="/wiremock/mgr-applications/platform/get-by-ids-query-regular-app.json";
  private static final String STUB_REGULAR_AND_PLATFORM_APP =
    "/wiremock/mgr-applications/platform/get-by-ids-query-regular-and-platform-app.json";

  @Autowired private PlatformLockService platformLockService;
  @Autowired private PlatformEntitlementRepository platformEntitlementRepository;
  @Autowired private PlatformApplicationDependencyRepository platformApplicationDependencyRepository;

  @Test
  @WireMockStub(scripts = {STUB_PLATFORM_APP, STUB_VALIDATE_DESCRIPTOR})
  void entitle_positive_platformApplicationIsEntitledAndRetryIsIdempotent() throws Exception {
    var result = entitlePlatform(PLATFORM_APP_V1).andExpect(status().isCreated())
      .andExpect(jsonPath("$.flowId", notNullValue()))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.platformEntitlements[*].applicationId", contains(PLATFORM_APP_V1)))
      .andReturn();

    assertPlatformFlow(getFlow(flowIdFromBody(result)),
      ExecutionStatus.FINISHED, EntitlementRequestType.PLATFORM_ENTITLE);
    assertThat(platformApplicationIds()).containsExactly(PLATFORM_APP_V1);

    entitlePlatform(PLATFORM_APP_V1).andExpect(status().isCreated())
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.platformEntitlements[*].applicationId", contains(PLATFORM_APP_V1)));
    assertThat(platformApplicationIds()).containsExactly(PLATFORM_APP_V1);
  }

  @Test
  @WireMockStub(scripts = {STUB_PLATFORM_APPS_DEPENDENCY_ORDER, STUB_VALIDATE_DESCRIPTOR})
  void entitle_positive_severalApplicationsAreEntitledInDependencyOrder() throws Exception {
    var result = entitlePlatform(PLATFORM_APP2_V1, PLATFORM_APP_V1).andExpect(status().isCreated())
      .andExpect(jsonPath("$.flowId", notNullValue()))
      .andExpect(jsonPath("$.totalRecords", is(2)))
      .andExpect(jsonPath("$.platformEntitlements[*].applicationId",
        containsInAnyOrder(PLATFORM_APP_V1, PLATFORM_APP2_V1)))
      .andReturn();

    var flowId = flowIdFromBody(result);
    assertPlatformFlow(getFlow(flowId), ExecutionStatus.FINISHED, EntitlementRequestType.PLATFORM_ENTITLE);
    assertThat(platformApplicationIds()).containsExactlyInAnyOrder(PLATFORM_APP_V1, PLATFORM_APP2_V1);
    var applicationFlows = getFlow(flowId).getApplicationFlows();
    assertThat(applicationFlows).extracting(ApplicationFlow::getApplicationId)
      .containsExactlyInAnyOrder(PLATFORM_APP_V1, PLATFORM_APP2_V1);
    assertThat(applicationFlows).extracting(ApplicationFlow::getStatus).containsOnly(ExecutionStatus.FINISHED);
    assertThat(applicationFlows).extracting(ApplicationFlow::getType).containsOnly(EntitlementType.PLATFORM_ENTITLE);
    assertThat(finishedAt(applicationFlows, PLATFORM_APP_V1))
      .isBeforeOrEqualTo(finishedAt(applicationFlows, PLATFORM_APP2_V1));
    assertThat(platformDependencies()).containsExactly(tuple(PLATFORM_APP2_V1, "platform-app", "^1.0.0"));
  }

  @Test
  @Sql("classpath:/sql/platform/platform-entitlement-v1.sql")
  @WireMockStub(scripts = {STUB_PLATFORM_APP2_DEPENDENCY_ENTITLED, STUB_VALIDATE_DESCRIPTOR})
  void entitle_positive_dependencyIsAlreadyEntitled() throws Exception {
    var result = entitlePlatform(PLATFORM_APP2_V1).andExpect(status().isCreated())
      .andExpect(jsonPath("$.flowId", notNullValue()))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.platformEntitlements[*].applicationId", contains(PLATFORM_APP2_V1)))
      .andReturn();

    var flowId = flowIdFromBody(result);
    assertPlatformFlow(getFlow(flowId), ExecutionStatus.FINISHED, EntitlementRequestType.PLATFORM_ENTITLE);
    assertThat(getFlow(flowId).getApplicationFlows()).extracting(ApplicationFlow::getApplicationId)
      .containsExactly(PLATFORM_APP2_V1);
    assertThat(platformApplicationIds()).containsExactlyInAnyOrder(PLATFORM_APP_V1, PLATFORM_APP2_V1);
    assertThat(platformDependencies()).containsExactly(tuple(PLATFORM_APP2_V1, "platform-app", "^1.0.0"));
  }

  @Test
  @WireMockStub(scripts = STUB_REGULAR_AND_PLATFORM_APP)
  void entitle_negative_oneRegularApplicationFailsTheWholeRequest() throws Exception {
    var result = entitlePlatform(PLATFORM_APP_V1, REGULAR_APP);

    expectStageFailure(result, "PlatformApplicationsValidator", "Application is not platform-scoped");
    assertPlatformFlow(getFlow(flowIdFromHeader(result)),
      ExecutionStatus.CANCELLED, EntitlementRequestType.PLATFORM_ENTITLE);
    assertThat(platformApplicationIds()).isEmpty();
  }

  @Test
  @WireMockStub(scripts = STUB_REGULAR)
  void entitle_negative_regularApplicationIsRejected() throws Exception {
    var result = entitlePlatform(REGULAR_APP);

    expectStageFailure(result, "PlatformApplicationsValidator", "Application is not platform-scoped");
    assertPlatformFlow(getFlow(flowIdFromHeader(result)),
      ExecutionStatus.CANCELLED, EntitlementRequestType.PLATFORM_ENTITLE);
    assertThat(platformApplicationIds()).isEmpty();
  }

  @Test
  @Sql("classpath:/sql/platform/platform-entitlement-v1.sql")
  @WireMockStub(scripts = STUB_PLATFORM_APP_V2)
  void entitle_negative_secondVersionIsRejectedWhileAnotherIsEntitled() throws Exception {
    var result = entitlePlatform(PLATFORM_APP_V2);

    expectStageFailure(result, "PlatformEntitlementNotExistValidator",
      "Another version of the application is already entitled");
    assertPlatformFlow(getFlow(flowIdFromHeader(result)),
      ExecutionStatus.CANCELLED, EntitlementRequestType.PLATFORM_ENTITLE);
    assertThat(platformApplicationIds()).containsExactly(PLATFORM_APP_V1);
  }

  @Test
  @Sql("classpath:/sql/platform/platform-base-v1-entitled.sql")
  @WireMockStub(scripts = {STUB_PLATFORM_APPS_ENTITLE_UNSATISFIED_DEPENDENCY, STUB_VALIDATE_DESCRIPTOR})
  void entitle_negative_secondLayerFails_firstLayerRowsAreRolledBack() throws Exception {
    var result = entitlePlatform(PLATFORM_APP_V1, PLATFORM_APP2_V1);

    expectStageFailure(result, "PlatformDependencyValidator",
      "The following platform application dependencies must be entitled first");
    assertPlatformFlow(getFlow(flowIdFromHeader(result)),
      ExecutionStatus.CANCELLED, EntitlementRequestType.PLATFORM_ENTITLE);
    assertThat(platformApplicationIds()).containsExactly(PLATFORM_BASE_V1);
    assertThat(platformDependencies()).isEmpty();
  }
  
  @Test
  @Sql("classpath:/sql/platform/platform-app-v1-entitled-with-platform-base-dependency.sql")
  @WireMockStub(scripts = {STUB_ENTITLED_PLATFORM_APP_V1, STUB_PLATFORM_APP_V2, STUB_VALIDATE_DESCRIPTOR})
  void upgrade_positive_replacesEntitledVersion() throws Exception {
    var result = upgradePlatform(PLATFORM_APP_V2).andExpect(status().isOk())
      .andExpect(jsonPath("$.flowId", notNullValue()))
      .andExpect(jsonPath("$.totalRecords", is(1)))
      .andExpect(jsonPath("$.platformEntitlements[*].applicationId", contains(PLATFORM_APP_V2)))
      .andReturn();

    var flow = getFlow(flowIdFromBody(result));
    assertPlatformFlow(flow, ExecutionStatus.FINISHED, EntitlementRequestType.PLATFORM_UPGRADE);
    assertThat(flow.getApplicationFlows()).extracting(ApplicationFlow::getApplicationId)
      .containsExactly(PLATFORM_APP_V2);
    assertThat(flow.getApplicationFlows()).extracting(ApplicationFlow::getStatus)
      .containsOnly(ExecutionStatus.FINISHED);
    assertThat(flow.getApplicationFlows()).extracting(ApplicationFlow::getType)
      .containsOnly(EntitlementType.PLATFORM_UPGRADE);
    assertThat(platformApplicationIds()).containsExactlyInAnyOrder(PLATFORM_BASE_V1, PLATFORM_APP_V2);
    assertThat(platformDependencies()).containsExactly(tuple(PLATFORM_APP_V2, "platform-base", "~1.0.0"));
  }

  @Test
  @Sql("classpath:/sql/platform/platform-entitlement-with-dependent.sql")
  @WireMockStub(scripts = {STUB_ENTITLED_PLATFORM_APP_V1, STUB_PLATFORM_APP_V2})
  void upgrade_negative_incompatibleDependentRejectsUpgradeAndAppliesNoChange() throws Exception {
    var result = upgradePlatform(PLATFORM_APP_V2);

    expectStageFailure(result, "PlatformTenantDependentsValidator",
      "Platform application upgrade is incompatible with dependent applications");
    expectStageFailure(result, "PlatformTenantDependentsValidator",
      "folio-app3-3.0.0, value: Requires platform-app ^1.0.0, which the upgrade to 2.0.0 does not satisfy");
    assertPlatformFlow(getFlow(flowIdFromHeader(result)),
      ExecutionStatus.CANCELLED, EntitlementRequestType.PLATFORM_UPGRADE);
    assertThat(platformApplicationIds()).containsExactly(PLATFORM_APP_V1);
  }

  @Test
  @WireMockStub(scripts = STUB_PLATFORM_APP_V2)
  void upgrade_negative_nothingIsEntitled() throws Exception {
    var result = upgradePlatform(PLATFORM_APP_V2);

    expectStageFailure(result, "PlatformEntitlementExistToUpgradeValidator",
      "No version of the application is entitled to upgrade");
    assertPlatformFlow(getFlow(flowIdFromHeader(result)),
      ExecutionStatus.CANCELLED, EntitlementRequestType.PLATFORM_UPGRADE);
    assertThat(platformApplicationIds()).isEmpty();
  }

  @Test
  @Sql("classpath:/sql/platform/platform-apps-v1-entitled-with-dependencies.sql")
  @WireMockStub(scripts = {STUB_ENTITLED_PLATFORM_APP_V1, STUB_ENTITLED_PLATFORM_APP2_V1,
    STUB_PLATFORM_APPS_V2_UNSATISFIED_DEPENDENCY, STUB_VALIDATE_DESCRIPTOR})
  void upgrade_negative_secondLayerFails_firstLayerRowsAreRolledBack() throws Exception {
    var result = upgradePlatform(PLATFORM_APP_V2, PLATFORM_APP2_V2);

    expectStageFailure(result, "PlatformDependencyValidator",
      "The following platform application dependencies must be entitled first");
    assertPlatformFlow(getFlow(flowIdFromHeader(result)),
      ExecutionStatus.CANCELLED, EntitlementRequestType.PLATFORM_UPGRADE);
    assertThat(platformApplicationIds())
      .containsExactlyInAnyOrder(PLATFORM_BASE_V1, PLATFORM_APP_V1, PLATFORM_APP2_V1);
    assertThat(platformDependencies()).containsExactlyInAnyOrder(
      tuple(PLATFORM_APP_V1, "platform-base", "^1.0.0"),
      tuple(PLATFORM_APP2_V1, "platform-app", "^1.0.0"));
  }

  @Test
  void tenantOperations_negative_rejectedWhilePlatformOperationHoldsLock() throws Exception {
    var holder = UUID.randomUUID();
    platformLockService.acquire(holder);
    try {
      for (var request : tenantWriteRequests()) {
        mockMvc.perform(request)
          .andExpect(status().isConflict())
          .andExpect(header().string(RETRY_AFTER, DEFAULT_RETRY_AFTER_SECONDS))
          .andExpect(jsonPath("$.errors[0].type", is("PlatformOperationInProgressException")));
      }

      assertThat(flowCount()).isZero();
    } finally {
      platformLockService.release(holder);
    }
  }

  @Test
  void platformOperations_negative_rejectedWhileAnotherPlatformOperationHoldsLock() throws Exception {
    var holder = UUID.randomUUID();
    platformLockService.acquire(holder);
    try {
      entitlePlatform(PLATFORM_APP_V1)
        .andExpect(status().isConflict())
        .andExpect(header().string(RETRY_AFTER, DEFAULT_RETRY_AFTER_SECONDS));
      upgradePlatform(PLATFORM_APP_V2)
        .andExpect(status().isConflict())
        .andExpect(header().string(RETRY_AFTER, DEFAULT_RETRY_AFTER_SECONDS));

      assertThat(flowCount()).isZero();
      assertThat(platformApplicationIds()).isEmpty();
    } finally {
      platformLockService.release(holder);
    }
  }

  @Test
  @WireMockStub(scripts = {
    STUB_PLATFORM_APP,
    STUB_REGULAR,
    STUB_VALIDATE_DESCRIPTOR,
    "/wiremock/mgr-tenants/test/get-404.json",
    "/wiremock/mgr-applications/folio-app1/get-by-ids-query-full.json"
  })
  void lock_positive_releasedAfterPlatformOperationSucceedsOrFails() throws Exception {
    // no platform operation in progress: a tenant request is not rejected by the lock, it fails later on its own
    // because the tenant is not found
    assertThat(platformLockService.isHeld()).isFalse();
    mockMvc.perform(withBody(post(path("/entitlements")), entitlementRequest(REGULAR_APP)))
      .andExpect(status().isBadRequest());

    entitlePlatform(PLATFORM_APP_V1).andExpect(status().isCreated());
    assertThat(platformLockService.isHeld()).isFalse();

    entitlePlatform(REGULAR_APP).andExpect(status().isBadRequest());
    assertThat(platformLockService.isHeld()).isFalse();

    mockMvc.perform(withBody(post(path("/entitlements")), entitlementRequest(REGULAR_APP)))
      .andExpect(status().isBadRequest());
  }

  private ResultActions entitlePlatform(String... applicationIds) throws Exception {
    return mockMvc.perform(withBody(post(path(PLATFORM_PATH)), platformBody(applicationIds)));
  }

  private ResultActions upgradePlatform(String... applicationIds) throws Exception {
    return mockMvc.perform(withBody(put(path(PLATFORM_PATH)), platformBody(applicationIds)));
  }

  private List<Tuple> platformDependencies() {
    return platformApplicationDependencyRepository.findAll().stream()
      .map(d -> tuple(d.getApplicationId(), d.getParentName(), d.getParentVersion()))
      .toList();
  }

  private List<String> platformApplicationIds() {
    return platformEntitlementRepository.findAll().stream()
      .map(PlatformEntitlementEntity::getApplicationId)
      .toList();
  }

  private static UUID flowIdFromBody(MvcResult result) throws Exception {
    return parseResponse(result, ExtendedPlatformEntitlements.class).getFlowId();
  }

  private static UUID flowIdFromHeader(ResultActions result) {
    return UUID.fromString(Objects.requireNonNull(result.andReturn().getResponse().getHeader(FLOW_ID_HEADER)));
  }

  private static void assertPlatformFlow(Flow flow, ExecutionStatus status, EntitlementRequestType type) {
    assertThat(flow.getStatus()).isEqualTo(status);
    assertThat(flow.getType()).isEqualTo(type);
    assertThat(flow.getTenantId()).isNull();
  }

  private static Date finishedAt(List<ApplicationFlow> applicationFlows, String applicationId) {
    return applicationFlows.stream()
      .filter(flow -> applicationId.equals(flow.getApplicationId()))
      .findFirst().orElseThrow().getFinishedAt();
  }

  private Flow getFlow(UUID flowId) throws Exception {
    var request = get(path("/entitlement-flows/" + flowId)).header(TOKEN, getSystemAccessToken());
    var result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    return parseResponse(result, Flow.class);
  }

  private int flowCount() throws Exception {
    var request = get(path("/entitlement-flows")).header(TOKEN, getSystemAccessToken());
    var result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    return parseResponse(result, Flows.class).getTotalRecords();
  }

  private static List<MockHttpServletRequestBuilder> tenantWriteRequests() {
    return List.of(
      withBody(post(path("/entitlements")), entitlementRequest(REGULAR_APP)),
      withBody(put(path("/entitlements")), entitlementRequest(REGULAR_APP)),
      withBody(delete(path("/entitlements")), entitlementRequest(REGULAR_APP)),
      withBody(put(path("/entitlements/state")), desiredStateRequest(REGULAR_APP)));
  }

  private static void expectStageFailure(ResultActions result, String stage, String message) throws Exception {
    result.andExpect(status().isBadRequest())
      .andExpect(jsonPath("$.errors[0].parameters[?(@.key == '" + stage + "')].value",
        hasItem(containsString(message))));
  }

  private static MockHttpServletRequestBuilder withBody(MockHttpServletRequestBuilder builder, Object body) {
    return builder.contentType(APPLICATION_JSON).header(TOKEN, getSystemAccessToken()).content(asJsonString(body));
  }

  private static PlatformEntitlementRequestBody platformBody(String... applicationIds) {
    return new PlatformEntitlementRequestBody().applications(List.of(applicationIds));
  }

  private static String path(String path) {
    return updatePathWithPrefix(path);
  }
}
