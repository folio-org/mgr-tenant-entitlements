package org.folio.entitlement.service.flow;

import static org.folio.entitlement.integration.folio.CommonStageContext.PARAM_REQUEST;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.stage.ApplicationDescriptorLoader;
import org.folio.entitlement.service.stage.CancellationFailedFlowFinalizer;
import org.folio.entitlement.service.stage.CancelledFlowFinalizer;
import org.folio.entitlement.service.stage.FailedFlowFinalizer;
import org.folio.entitlement.service.stage.FinishedFlowFinalizer;
import org.folio.entitlement.service.stage.FlowInitializer;
import org.folio.entitlement.service.stage.TenantLoader;
import org.folio.entitlement.service.validator.ApplicationFlowValidator;
import org.folio.entitlement.service.validator.ExistingEntitlementValidator;
import org.folio.flow.api.DynamicStage;
import org.folio.flow.api.Flow;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RevokeFlowFactory implements FlowFactory {

  private final TenantLoader tenantLoader;
  private final ApplicationsFlowFactory applicationsFlowFactory;
  private final ApplicationFlowValidator applicationFlowValidator;
  private final ApplicationFlowQueuingStage applicationFlowQueuingStage;
  private final ApplicationDescriptorLoader applicationDescriptorLoader;
  private final ExistingEntitlementValidator existingEntitlementValidator;

  private final FlowInitializer flowInitializer;
  private final FailedFlowFinalizer failedFlowFinalizer;
  private final FinishedFlowFinalizer finishedFlowFinalizer;
  private final CancelledFlowFinalizer cancelledFlowFinalizer;
  private final CancellationFailedFlowFinalizer cancellationFailedFlowFinalizer;

  @Override
  public Flow createFlow(EntitlementRequest request) {
    return Flow.builder()
      .id(UUID.randomUUID())
      .stage(flowInitializer)
      .stage(existingEntitlementValidator)
      .stage(applicationFlowValidator)
      .stage(tenantLoader)
      .stage(applicationDescriptorLoader)
      .stage(applicationFlowQueuingStage)
      .stage(DynamicStage.of(applicationsFlowFactory.getName(), applicationsFlowFactory::createFlow))
      .stage(finishedFlowFinalizer)
      .onFlowError(failedFlowFinalizer)
      .onFlowCancellation(cancelledFlowFinalizer)
      .onFlowCancellationError(cancellationFailedFlowFinalizer)
      .flowParameter(PARAM_REQUEST, request)
      .executionStrategy(request.getExecutionStrategy())
      .build();
  }
}
