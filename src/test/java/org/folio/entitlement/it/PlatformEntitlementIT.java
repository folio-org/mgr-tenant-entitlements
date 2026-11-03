package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.common.utils.OkapiHeaders.TOKEN;
import static org.folio.entitlement.support.TestUtils.asJsonString;
import static org.folio.entitlement.support.TestUtils.parseResponse;
import static org.folio.entitlement.support.TestValues.desiredStateRequest;
import static org.folio.entitlement.support.TestValues.emptyEntitlements;
import static org.folio.entitlement.support.TestValues.entitlementRequest;
import static org.folio.entitlement.support.TestValues.queryByTenantAndAppId;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
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

import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.ExtendedPlatformEntitlements;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.dto.Flows;
import org.folio.entitlement.domain.dto.PlatformEntitlementRequestBody;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Integration tests for deployment-wide platform application entitlements and the platform lock.
 *
 * <p>Platform applications are changed only through {@code /entitlements/platform}; tenant-scoped writes are rejected
 * with 409 while a platform operation holds the lock. The lock is held directly through {@link PlatformLockService}
 * in the tests that need an operation "in progress", which keeps them deterministic.</p>
 */
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
  private static final String REGULAR_APP = "folio-app1-1.0.0";
  private static final String RETRY_AFTER = "Retry-After";
  private static final String DEFAULT_RETRY_AFTER_SECONDS = "5";

  private static final String STUB_PLATFORM_V1 = "/wiremock/mgr-applications/platform/get-platform-app-v1.json";
  private static final String STUB_PLATFORM_V2 = "/wiremock/mgr-applications/platform/get-platform-app-v2.json";
  private static final String STUB_REGULAR = "/wiremock/mgr-applications/platform/get-regular-app.json";

  @Autowired private PlatformLockService platformLockService;
  @Autowired private PlatformEntitlementRepository platformEntitlementRepository;

  @Test
  @WireMockStub(scripts = STUB_PLATFORM_V1)
  void entitle_positive_platformApplicationIsEntitledAndRetryIsIdempotent() throws Exception {
    var result = entitlePlatform(PLATFORM_APP_V1).andExpect(status().isCreated()).andReturn();

    var flowId = parseResponse(result, ExtendedPlatformEntitlements.class).getFlowId();
    assertThat(flowId).isNotNull();
    var flow = getFlow(flowId);
    assertThat(flow.getStatus()).isEqualTo(ExecutionStatus.FINISHED);
    assertThat(flow.getType()).isEqualTo(EntitlementRequestType.ENTITLE);
    assertThat(flow.getTenantId()).isNull();
    assertThat(platformApplicationIds()).containsExactly(PLATFORM_APP_V1);

    entitlePlatform(PLATFORM_APP_V1).andExpect(status().isCreated());
    assertThat(platformApplicationIds()).containsExactly(PLATFORM_APP_V1);
  }

  @Test
  @WireMockStub(scripts = STUB_REGULAR)
  void entitle_negative_regularApplicationIsRejected() throws Exception {
    expectStageFailure(entitlePlatform(REGULAR_APP), "PlatformScopeValidator", "Application is not platform-scoped");

    assertThat(platformApplicationIds()).isEmpty();
  }

  @Test
  @Sql("classpath:/sql/platform/platform-entitlement-v1.sql")
  @WireMockStub(scripts = STUB_PLATFORM_V2)
  void entitle_negative_secondVersionIsRejectedWhileAnotherIsEntitled() throws Exception {
    expectStageFailure(entitlePlatform(PLATFORM_APP_V2), "PlatformEntitleValidator",
      "Another version of the application is already entitled");

    assertThat(platformApplicationIds()).containsExactly(PLATFORM_APP_V1);
  }

  @Test
  @Sql("classpath:/sql/platform/platform-entitlement-v1.sql")
  @WireMockStub(scripts = STUB_PLATFORM_V2)
  void upgrade_positive_replacesEntitledVersion() throws Exception {
    upgradePlatform(PLATFORM_APP_V2).andExpect(status().isOk());

    assertThat(platformApplicationIds()).containsExactly(PLATFORM_APP_V2);
  }

  @Test
  @Sql("classpath:/sql/platform/platform-entitlement-with-dependent.sql")
  @WireMockStub(scripts = STUB_PLATFORM_V2)
  void upgrade_negative_incompatibleDependentRejectsUpgradeAndAppliesNoChange() throws Exception {
    expectStageFailure(upgradePlatform(PLATFORM_APP_V2), "PlatformDependentsValidator",
      "Platform application upgrade is incompatible with dependent applications");

    assertThat(platformApplicationIds()).containsExactly(PLATFORM_APP_V1);
  }

  @Test
  @WireMockStub(scripts = STUB_PLATFORM_V2)
  void upgrade_negative_nothingIsEntitled() throws Exception {
    expectStageFailure(upgradePlatform(PLATFORM_APP_V2), "PlatformUpgradeValidator",
      "No version of the application is entitled to upgrade");

    assertThat(platformApplicationIds()).isEmpty();
  }

  @Test
  @WireMockStub(scripts = {
    "/wiremock/mgr-tenants/test/get.json",
    "/wiremock/mgr-applications/platform/get-by-ids-query-platform-app-v1.json"
  })
  void desiredState_negative_platformApplicationIsRejected() throws Exception {
    var result = mockMvc.perform(withBody(put(path("/entitlements/state")), desiredStateRequest(PLATFORM_APP_V1)));

    expectStageFailure(result, "PlatformStateRequestValidator",
      "Desired state request contains platform-scoped applications");
    getEntitlementsByQuery(queryByTenantAndAppId(PLATFORM_APP_V1), emptyEntitlements());
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
    STUB_PLATFORM_V1,
    STUB_REGULAR,
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

  private ResultActions entitlePlatform(String applicationId) throws Exception {
    return mockMvc.perform(withBody(post(path(PLATFORM_PATH)), platformBody(applicationId)));
  }

  private ResultActions upgradePlatform(String applicationId) throws Exception {
    return mockMvc.perform(withBody(put(path(PLATFORM_PATH)), platformBody(applicationId)));
  }

  private List<String> platformApplicationIds() {
    return platformEntitlementRepository.findAll().stream()
      .map(PlatformEntitlementEntity::getApplicationId)
      .toList();
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

  private static PlatformEntitlementRequestBody platformBody(String applicationId) {
    return new PlatformEntitlementRequestBody().applications(List.of(applicationId));
  }

  private static String path(String path) {
    return updatePathWithPrefix(path);
  }
}
