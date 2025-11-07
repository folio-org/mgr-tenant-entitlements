package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyMap;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.STATE;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;

import java.util.List;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.integration.kafka.KafkaTenantTopicCreator;
import org.folio.entitlement.service.stage.ApplicationStateTransitionPlanner;
import org.folio.entitlement.service.stage.CancellationFailedFlowFinalizer;
import org.folio.entitlement.service.stage.CancelledFlowFinalizer;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.entitlement.service.stage.DesiredStateApplicationDescriptorLoader;
import org.folio.entitlement.service.stage.FailedFlowFinalizer;
import org.folio.entitlement.service.stage.FinishedFlowFinalizer;
import org.folio.entitlement.service.stage.FlowInitializer;
import org.folio.entitlement.service.stage.TenantLoader;
import org.folio.entitlement.service.validator.DesiredStateApplicationFlowValidator;
import org.folio.entitlement.service.validator.DesiredStateWithRevokeValidator;
import org.folio.entitlement.service.validator.DesiredStateWithUpgradeValidator;
import org.folio.entitlement.service.validator.StageRequestValidator;
import org.folio.flow.api.FlowEngine;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DesiredStateFlowFactoryTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("test-flow-engine", false);

  @InjectMocks private DesiredStateFlowFactory flowFactory;

  @Mock private TenantLoader tenantLoader;
  @Mock private ApplicationStateTransitionPlanner applicationStateTransitionPlanner;
  @Mock private DesiredStateApplicationDescriptorLoader applicationDescriptorLoader;
  @Mock private StageRequestValidator interfaceIntegrityValidator;
  @Mock private DesiredStateApplicationFlowValidator desiredStateApplicationFlowValidator;
  @Mock private DesiredStateWithUpgradeValidator desiredStateWithUpgradeValidator;
  @Mock private DesiredStateWithRevokeValidator desiredStateWithRevokeValidator;
  @Mock private DesiredStateApplicationFlowQueuingStage applicationFlowQueuingStage;
  @Mock private KafkaTenantTopicCreator kafkaTenantTopicCreator;
  @Mock private DesiredStateApplicationsFlowProvider applicationsFlowProvider;
  @Mock private FinishedFlowFinalizer finishedFlowFinalizer;
  @Mock private FlowInitializer flowInitializer;
  @Mock private FailedFlowFinalizer failedFlowFinalizer;
  @Mock private CancelledFlowFinalizer cancelledFlowFinalizer;
  @Mock private CancellationFailedFlowFinalizer cancellationFailedFlowFinalizer;

  @Test
  void createFlow_positive() {
    mockStageNames(
      flowInitializer, tenantLoader, applicationStateTransitionPlanner, applicationDescriptorLoader,
      interfaceIntegrityValidator, desiredStateApplicationFlowValidator, desiredStateWithUpgradeValidator,
      desiredStateWithRevokeValidator, applicationFlowQueuingStage, kafkaTenantTopicCreator,
      finishedFlowFinalizer, failedFlowFinalizer, cancelledFlowFinalizer, cancellationFailedFlowFinalizer);

    var request = EntitlementRequest.builder()
      .type(STATE)
      .tenantId(TENANT_ID)
      .applications(List.of("app-1.0.0"))
      .build();

    var flow = flowFactory.createFlow(request);
    flowEngine.execute(flow);

    var context = commonStageContext(flow.getId(), flow.getFlowParameters(), emptyMap());

    var inOrder = Mockito.inOrder(
      flowInitializer, tenantLoader, applicationStateTransitionPlanner, applicationDescriptorLoader,
      interfaceIntegrityValidator, desiredStateApplicationFlowValidator, desiredStateWithUpgradeValidator,
      desiredStateWithRevokeValidator, applicationFlowQueuingStage, kafkaTenantTopicCreator,
      applicationsFlowProvider, finishedFlowFinalizer);

    inOrder.verify(applicationsFlowProvider).getName();
    verifyStageExecution(inOrder, flowInitializer, context);
    verifyStageExecution(inOrder, tenantLoader, context);
    verifyStageExecution(inOrder, applicationStateTransitionPlanner, context);
    verifyStageExecution(inOrder, applicationDescriptorLoader, context);
    verifyStageExecution(inOrder, interfaceIntegrityValidator, context);
    verifyStageExecution(inOrder, desiredStateApplicationFlowValidator, context);
    verifyStageExecution(inOrder, desiredStateWithUpgradeValidator, context);
    verifyStageExecution(inOrder, desiredStateWithRevokeValidator, context);
    verifyStageExecution(inOrder, applicationFlowQueuingStage, context);
    verifyStageExecution(inOrder, kafkaTenantTopicCreator, context);
    inOrder.verify(applicationsFlowProvider).createFlow(context);
    verifyStageExecution(inOrder, finishedFlowFinalizer, context);
  }

  private static <T extends IdentifiableStageContext> void verifyStageExecution(InOrder inOrder,
    DatabaseLoggingStage<T> stage, T context) {
    inOrder.verify(stage).onStart(context);
    inOrder.verify(stage).execute(context);
    inOrder.verify(stage).onSuccess(context);
  }
}
