package org.folio.entitlement.service.flow;

import static java.util.Collections.emptySet;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.ApplicationStateTransitionBucket.entitle;
import static org.folio.entitlement.domain.model.ApplicationStateTransitionBucket.revoke;
import static org.folio.entitlement.domain.model.ApplicationStateTransitionBucket.upgrade;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.verifyNoMoreInteractions;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.model.ApplicationStateTransitionBucket;
import org.folio.entitlement.domain.model.ApplicationStateTransitionPlan;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DesiredStateApplicationFlowQueuingStageTest {

  private static final String APP1_ID = "app1-1.0.0";
  private static final String APP2_ID = "app2-1.0.0";
  private static final String APP3_ID = "app3-1.0.0";
  private static final UUID APP1_FLOW_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
  private static final UUID APP2_FLOW_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
  private static final UUID APP3_FLOW_ID = UUID.fromString("33333333-3333-3333-3333-333333333333");

  @InjectMocks private DesiredStateApplicationFlowQueuingStage stage;
  @Mock private ApplicationFlowService applicationFlowService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_entitleBucketOnly() {
    var plan = createPlan(entitle(Set.of(APP1_ID)), upgrade(emptySet()), revoke(Set.of()));
    var context = createContext(plan);
    var flow1 = createApplicationFlow(APP1_FLOW_ID, APP1_ID, ENTITLE);

    when(applicationFlowService.createQueuedApplicationFlows(FLOW_ID, Set.of(APP1_ID), ENTITLE, TENANT_ID))
      .thenReturn(List.of(flow1));

    stage.execute(context);

    var queuedFlows = context.getQueuedApplicationFlows();
    assertThat(queuedFlows).containsOnly(entry(APP1_ID, APP1_FLOW_ID));
  }

  @Test
  void execute_positive_revokeBucketOnly() {
    var plan = createPlan(entitle(emptySet()), upgrade(emptySet()), revoke(Set.of(APP1_ID)));
    var context = createContext(plan);
    var flow1 = createApplicationFlow(APP1_FLOW_ID, APP1_ID, REVOKE);

    when(applicationFlowService.createQueuedApplicationFlows(FLOW_ID, Set.of(APP1_ID), REVOKE, TENANT_ID))
      .thenReturn(List.of(flow1));

    stage.execute(context);

    var queuedFlows = context.getQueuedApplicationFlows();
    assertThat(queuedFlows).containsOnly(entry(APP1_ID, APP1_FLOW_ID));
  }

  @Test
  void execute_positive_upgradeBucketOnly() {
    var plan = createPlan(entitle(emptySet()), upgrade(Set.of(APP1_ID)), revoke(emptySet()));
    var context = createContext(plan);
    var flow1 = createApplicationFlow(APP1_FLOW_ID, APP1_ID, UPGRADE);

    when(applicationFlowService.createQueuedApplicationFlows(FLOW_ID, Set.of(APP1_ID), UPGRADE, TENANT_ID))
      .thenReturn(List.of(flow1));

    stage.execute(context);

    var queuedFlows = context.getQueuedApplicationFlows();
    assertThat(queuedFlows).containsOnly(entry(APP1_ID, APP1_FLOW_ID));
  }

  @Test
  void execute_positive_multipleBuckets() {
    var plan = createPlan(entitle(Set.of(APP1_ID)), upgrade(Set.of(APP2_ID)), revoke(Set.of(APP3_ID)));
    var context = createContext(plan);
    var flow1 = createApplicationFlow(APP1_FLOW_ID, APP1_ID, ENTITLE);
    var flow2 = createApplicationFlow(APP2_FLOW_ID, APP2_ID, UPGRADE);
    var flow3 = createApplicationFlow(APP3_FLOW_ID, APP3_ID, REVOKE);

    when(applicationFlowService.createQueuedApplicationFlows(FLOW_ID, Set.of(APP1_ID), ENTITLE, TENANT_ID))
      .thenReturn(List.of(flow1));
    when(applicationFlowService.createQueuedApplicationFlows(FLOW_ID, Set.of(APP2_ID), UPGRADE, TENANT_ID))
      .thenReturn(List.of(flow2));
    when(applicationFlowService.createQueuedApplicationFlows(FLOW_ID, Set.of(APP3_ID), REVOKE, TENANT_ID))
      .thenReturn(List.of(flow3));

    stage.execute(context);

    var queuedFlows = context.getQueuedApplicationFlows();
    assertThat(queuedFlows).containsOnly(entry(APP1_ID, APP1_FLOW_ID), entry(APP2_ID, APP2_FLOW_ID),
      entry(APP3_ID, APP3_FLOW_ID));
  }

  @Test
  void execute_positive_multipleApplicationsInSameBucket() {
    var plan = createPlan(entitle(Set.of(APP1_ID, APP2_ID)), upgrade(emptySet()), revoke(emptySet()));
    var context = createContext(plan);
    var flow1 = createApplicationFlow(APP1_FLOW_ID, APP1_ID, ENTITLE);
    var flow2 = createApplicationFlow(APP2_FLOW_ID, APP2_ID, ENTITLE);

    when(applicationFlowService.createQueuedApplicationFlows(FLOW_ID, Set.of(APP1_ID, APP2_ID), ENTITLE, TENANT_ID))
      .thenReturn(List.of(flow1, flow2));

    stage.execute(context);

    var queuedFlows = context.getQueuedApplicationFlows();
    assertThat(queuedFlows).containsOnly(entry(APP1_ID, APP1_FLOW_ID), entry(APP2_ID, APP2_FLOW_ID));
  }

  @Test
  void execute_positive_allBucketsEmpty() {
    var plan = createPlan(entitle(emptySet()), upgrade(emptySet()), revoke(emptySet()));
    var context = createContext(plan);

    stage.execute(context);

    var queuedFlows = context.getQueuedApplicationFlows();
    assertThat(queuedFlows).isEmpty();
  }

  private static CommonStageContext createContext(ApplicationStateTransitionPlan plan) {
    var request = EntitlementRequest.builder()
      .type(EntitlementRequestType.STATE)
      .tenantId(TENANT_ID)
      .build();

    var flowParams = Map.of(PARAM_REQUEST, request);
    var context = commonStageContext(FLOW_ID, flowParams, Collections.emptyMap());
    context.withApplicationStateTransitionPlan(plan);
    return context;
  }

  private static ApplicationStateTransitionPlan createPlan(
    ApplicationStateTransitionBucket entitleBucket,
    ApplicationStateTransitionBucket upgradeBucket,
    ApplicationStateTransitionBucket revokeBucket) {
    return new ApplicationStateTransitionPlan(entitleBucket, upgradeBucket, revokeBucket);
  }

  private static ApplicationFlow createApplicationFlow(UUID flowId, String applicationId, EntitlementType type) {
    return new ApplicationFlow()
      .id(flowId)
      .flowId(FLOW_ID)
      .applicationId(applicationId)
      .tenantId(TENANT_ID)
      .status(ExecutionStatus.QUEUED)
      .type(type);
  }
}
