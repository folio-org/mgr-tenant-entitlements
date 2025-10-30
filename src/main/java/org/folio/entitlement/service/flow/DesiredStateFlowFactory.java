package org.folio.entitlement.service.flow;

import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.kafka.KafkaTenantTopicCreator;
import org.folio.entitlement.service.stage.ApplicationStateTransitionPlanMaker;
import org.folio.entitlement.service.stage.CancellationFailedFlowFinalizer;
import org.folio.entitlement.service.stage.CancelledFlowFinalizer;
import org.folio.entitlement.service.stage.DesiredStateApplicationDescriptorLoader;
import org.folio.entitlement.service.stage.FailedFlowFinalizer;
import org.folio.entitlement.service.stage.FinishedFlowFinalizer;
import org.folio.entitlement.service.stage.FlowInitializer;
import org.folio.entitlement.service.stage.TenantLoader;
import org.folio.entitlement.service.validator.DesiredStateApplicationFlowValidator;
import org.folio.entitlement.service.validator.DesiredStateWithRevokeValidator;
import org.folio.entitlement.service.validator.DesiredStateWithUpgradeValidator;
import org.folio.entitlement.service.validator.StageRequestValidator;
import org.folio.flow.api.DynamicStage;
import org.folio.flow.api.Flow;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DesiredStateFlowFactory implements FlowFactory {

  private final TenantLoader tenantLoader;
  private final ApplicationStateTransitionPlanMaker applicationStateTransitionPlanMaker;
  private final DesiredStateApplicationDescriptorLoader applicationDescriptorLoader;
  @Qualifier("stateInterfaceIntegrityValidator")
  private final StageRequestValidator interfaceIntegrityValidator;
  private final DesiredStateApplicationFlowValidator desiredStateApplicationFlowValidator;
  private final DesiredStateWithUpgradeValidator desiredStateWithUpgradeValidator;
  private final DesiredStateWithRevokeValidator desiredStateWithRevokeValidator;
  private final DesiredStateApplicationFlowQueuingStage applicationFlowQueuingStage;
  private final KafkaTenantTopicCreator kafkaTenantTopicCreator;
  private final DesiredStateApplicationsFlowProvider applicationsFlowProvider;

  private final FinishedFlowFinalizer finishedFlowFinalizer;
  private final FlowInitializer flowInitializer;
  private final FailedFlowFinalizer failedFlowFinalizer;
  private final CancelledFlowFinalizer cancelledFlowFinalizer;
  private final CancellationFailedFlowFinalizer cancellationFailedFlowFinalizer;

  @Override
  public Flow createFlow(EntitlementRequest request) {
    return Flow.builder()
      .id(UUID.randomUUID())
      .stage(flowInitializer)
      .stage(tenantLoader)
      .stage(applicationStateTransitionPlanMaker)
      .stage(applicationDescriptorLoader)
      // validation stages
      .stage(interfaceIntegrityValidator)
      .stage(desiredStateApplicationFlowValidator)
      .stage(desiredStateWithUpgradeValidator)
      .stage(desiredStateWithRevokeValidator)
      // preparation stages
      .stage(applicationFlowQueuingStage)
      .stage(kafkaTenantTopicCreator)
      // applications processing stage
      .stage(DynamicStage.of(applicationsFlowProvider.getName(), applicationsFlowProvider::createFlow))
      .stage(finishedFlowFinalizer)
      .onFlowError(failedFlowFinalizer)
      .onFlowCancellation(cancelledFlowFinalizer)
      .onFlowCancellationError(cancellationFailedFlowFinalizer)
      .flowParameter(PARAM_REQUEST, request)
      .executionStrategy(request.getExecutionStrategy())
      .build();
  }
}
