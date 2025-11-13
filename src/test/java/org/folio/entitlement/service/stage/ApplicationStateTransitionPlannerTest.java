package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.verifyNoMoreInteractions;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.ApplicationStateTransitionBucket;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationStateTransitionPlannerTest {

  private static final String APP_FOO_V1_ID = "app-foo-1.0.0";
  private static final String APP_FOO_V2_ID = "app-foo-2.0.0";
  private static final String APP_BAR_V1_ID = "app-bar-1.0.0";
  private static final String APP_BAR_V2_ID = "app-bar-2.0.0";
  private static final String APP_BAZ_V1_ID = "app-baz-1.0.0";
  private static final String APP_QUX_V1_ID = "app-qux-1.0.0";

  @InjectMocks private ApplicationStateTransitionPlanner planner;
  @Mock private EntitlementCrudService entitlementCrudService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_noExistingEntitlements_shouldEntitleAll() {
    var requestedApps = List.of(APP_FOO_V1_ID, APP_BAR_V1_ID);
    var context = createContext(requestedApps);

    when(entitlementCrudService.findByTenantId(TENANT_ID)).thenReturn(emptyList());

    planner.execute(context);

    var plan = context.getApplicationStateTransitionPlan();
    assertThat(plan).isNotNull();
    assertThat(plan.entitleBucket()).isNotNull();
    assertThat(plan.entitleBucket().getApplicationIds()).containsExactlyInAnyOrder(APP_FOO_V1_ID, APP_BAR_V1_ID);
    assertIsEmpty(plan.upgradeBucket());
    assertIsEmpty(plan.revokeBucket());
  }

  @Test
  void execute_positive_upgradeExistingApplication() {
    var requestedApps = List.of(APP_FOO_V2_ID);
    var entitlements = List.of(entitlement(APP_FOO_V1_ID));
    var context = createContext(requestedApps);

    when(entitlementCrudService.findByTenantId(TENANT_ID)).thenReturn(entitlements);

    planner.execute(context);

    var plan = context.getApplicationStateTransitionPlan();
    assertThat(plan).isNotNull();
    assertIsEmpty(plan.entitleBucket());
    assertThat(plan.upgradeBucket()).isNotNull();
    assertThat(plan.upgradeBucket().getApplicationIds()).containsExactlyInAnyOrder(APP_FOO_V2_ID);
    assertIsEmpty(plan.revokeBucket());
  }

  @Test
  void execute_positive_revokeAllApplications() {
    var requestedApps = List.<String>of();
    var entitlements = List.of(entitlement(APP_FOO_V1_ID), entitlement(APP_BAR_V1_ID));
    var context = createContext(requestedApps);

    when(entitlementCrudService.findByTenantId(TENANT_ID)).thenReturn(entitlements);

    planner.execute(context);

    var plan = context.getApplicationStateTransitionPlan();
    assertThat(plan).isNotNull();
    assertIsEmpty(plan.entitleBucket());
    assertIsEmpty(plan.upgradeBucket());
    assertThat(plan.revokeBucket()).isNotNull();
    assertThat(plan.revokeBucket().getApplicationIds()).containsExactlyInAnyOrder(APP_FOO_V1_ID, APP_BAR_V1_ID);
  }

  @Test
  void execute_positive_entitleUpgradeAndRevoke() {
    var requestedApps = List.of(APP_FOO_V2_ID, APP_BAZ_V1_ID);
    var entitlements = List.of(entitlement(APP_FOO_V1_ID), entitlement(APP_BAR_V1_ID));
    var context = createContext(requestedApps);

    when(entitlementCrudService.findByTenantId(TENANT_ID)).thenReturn(entitlements);

    planner.execute(context);

    var plan = context.getApplicationStateTransitionPlan();
    assertThat(plan).isNotNull();
    assertThat(plan.entitleBucket()).isNotNull();
    assertThat(plan.entitleBucket().getApplicationIds()).containsExactlyInAnyOrder(APP_BAZ_V1_ID);
    assertThat(plan.upgradeBucket()).isNotNull();
    assertThat(plan.upgradeBucket().getApplicationIds()).containsExactlyInAnyOrder(APP_FOO_V2_ID);
    assertThat(plan.revokeBucket()).isNotNull();
    assertThat(plan.revokeBucket().getApplicationIds()).containsExactlyInAnyOrder(APP_BAR_V1_ID);
  }

  @Test
  void execute_positive_sameVersionNoChange() {
    var requestedApps = List.of(APP_FOO_V1_ID);
    var entitlements = List.of(entitlement(APP_FOO_V1_ID));
    var context = createContext(requestedApps);

    when(entitlementCrudService.findByTenantId(TENANT_ID)).thenReturn(entitlements);

    planner.execute(context);

    var plan = context.getApplicationStateTransitionPlan();
    assertThat(plan).isNotNull();
    assertIsEmpty(plan.entitleBucket());
    assertIsEmpty(plan.upgradeBucket());
    assertIsEmpty(plan.revokeBucket());
  }

  @Test
  void execute_positive_multipleUpgrades() {
    var requestedApps = List.of(APP_FOO_V2_ID, APP_BAR_V2_ID);
    var entitlements = List.of(entitlement(APP_FOO_V1_ID), entitlement(APP_BAR_V1_ID));
    var context = createContext(requestedApps);

    when(entitlementCrudService.findByTenantId(TENANT_ID)).thenReturn(entitlements);

    planner.execute(context);

    var plan = context.getApplicationStateTransitionPlan();
    assertThat(plan).isNotNull();
    assertIsEmpty(plan.entitleBucket());
    assertThat(plan.upgradeBucket()).isNotNull();
    assertThat(plan.upgradeBucket().getApplicationIds()).containsExactlyInAnyOrder(APP_FOO_V2_ID, APP_BAR_V2_ID);
    assertIsEmpty(plan.revokeBucket());
  }

  @Test
  void execute_positive_entitleAndRevoke() {
    var requestedApps = List.of(APP_BAZ_V1_ID, APP_QUX_V1_ID);
    var entitlements = List.of(entitlement(APP_FOO_V1_ID), entitlement(APP_BAR_V1_ID));
    var context = createContext(requestedApps);

    when(entitlementCrudService.findByTenantId(TENANT_ID)).thenReturn(entitlements);

    planner.execute(context);

    var plan = context.getApplicationStateTransitionPlan();
    assertThat(plan).isNotNull();
    assertThat(plan.entitleBucket()).isNotNull();
    assertThat(plan.entitleBucket().getApplicationIds()).containsExactlyInAnyOrder(APP_BAZ_V1_ID, APP_QUX_V1_ID);
    assertIsEmpty(plan.upgradeBucket());
    assertThat(plan.revokeBucket()).isNotNull();
    assertThat(plan.revokeBucket().getApplicationIds()).containsExactlyInAnyOrder(APP_FOO_V1_ID, APP_BAR_V1_ID);
  }

  @ParameterizedTest(name = "[{index}] {0}")
  @MethodSource("complexScenariosDataProvider")
  void execute_positive_complexScenarios(@SuppressWarnings("unused") String testName,
    List<String> requestedApps, List<Entitlement> entitlements,
    List<String> expectedEntitle, List<String> expectedUpgrade, List<String> expectedRevoke) {
    var context = createContext(requestedApps);

    when(entitlementCrudService.findByTenantId(TENANT_ID)).thenReturn(entitlements);

    planner.execute(context);

    var plan = context.getApplicationStateTransitionPlan();
    assertThat(plan).isNotNull();

    if (expectedEntitle.isEmpty()) {
      assertIsEmpty(plan.entitleBucket());
    } else {
      assertThat(plan.entitleBucket()).isNotNull();
      assertThat(plan.entitleBucket().getApplicationIds()).containsExactlyInAnyOrderElementsOf(expectedEntitle);
    }

    if (expectedUpgrade.isEmpty()) {
      assertIsEmpty(plan.upgradeBucket());
    } else {
      assertThat(plan.upgradeBucket()).isNotNull();
      assertThat(plan.upgradeBucket().getApplicationIds()).containsExactlyInAnyOrderElementsOf(expectedUpgrade);
    }

    if (expectedRevoke.isEmpty()) {
      assertIsEmpty(plan.revokeBucket());
    } else {
      assertThat(plan.revokeBucket()).isNotNull();
      assertThat(plan.revokeBucket().getApplicationIds()).containsExactlyInAnyOrderElementsOf(expectedRevoke);
    }
  }

  private static Stream<Arguments> complexScenariosDataProvider() {
    return Stream.of(
      arguments("entitle one, keep one",
        List.of(APP_FOO_V1_ID, APP_BAR_V1_ID),
        List.of(entitlement(APP_FOO_V1_ID)),
        List.of(APP_BAR_V1_ID),
        emptyList(),
        emptyList()
      ),
      arguments("upgrade one, keep one",
        List.of(APP_FOO_V2_ID, APP_BAR_V1_ID),
        List.of(entitlement(APP_FOO_V1_ID), entitlement(APP_BAR_V1_ID)),
        emptyList(),
        List.of(APP_FOO_V2_ID),
        emptyList()
      ),
      arguments("keep one, revoke one",
        List.of(APP_FOO_V1_ID),
        List.of(entitlement(APP_FOO_V1_ID), entitlement(APP_BAR_V1_ID)),
        emptyList(),
        emptyList(),
        List.of(APP_BAR_V1_ID)
      ),
      arguments("entitle one, upgrade one, revoke one, keep one",
        List.of(APP_FOO_V2_ID, APP_BAR_V1_ID, APP_BAZ_V1_ID),
        List.of(entitlement(APP_FOO_V1_ID), entitlement(APP_BAR_V1_ID), entitlement(APP_QUX_V1_ID)),
        List.of(APP_BAZ_V1_ID),
        List.of(APP_FOO_V2_ID),
        List.of(APP_QUX_V1_ID)
      )
    );
  }

  private static CommonStageContext createContext(List<String> requestedApps) {
    var request = EntitlementRequest.builder()
      .type(EntitlementRequestType.STATE)
      .tenantId(TENANT_ID)
      .applications(requestedApps)
      .build();

    var flowParams = Map.of(PARAM_REQUEST, request);
    return commonStageContext(FLOW_ID, flowParams, emptyMap());
  }

  private static void assertIsEmpty(ApplicationStateTransitionBucket bucket) {
    assertThat(bucket).isNotNull();
    assertThat(bucket.isEmpty()).isTrue();
  }
}
