package org.folio.entitlement.service.flow;

import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.stage.ApplicationDescriptorLoader;
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
  private final BaseApplicationsFlowProvider applicationsFlowFactory;
  private final ApplicationFlowValidator applicationFlowValidator;
  private final ApplicationFlowQueuingStage applicationFlowQueuingStage;
  private final ApplicationDescriptorLoader applicationDescriptorLoader;
  private final ExistingEntitlementValidator existingEntitlementValidator;

  private final FlowInitializer flowInitializer;
  private final FinishedFlowFinalizer finishedFlowFinalizer;
  private final FlowFinalizerCallbacks finalizerCallbacks;

  @Override
  public Flow createFlow(EntitlementRequest request) {
    var builder = Flow.builder()
      .id(UUID.randomUUID())
      .stage(flowInitializer)
      .stage(existingEntitlementValidator)
      .stage(applicationFlowValidator)
      .stage(tenantLoader)
      .stage(applicationDescriptorLoader)
      .stage(applicationFlowQueuingStage)
      .stage(DynamicStage.of(applicationsFlowFactory.getClass().getSimpleName(), applicationsFlowFactory::createFlow))
      .stage(finishedFlowFinalizer);

    return finalizerCallbacks.apply(builder)
      .flowParameter(PARAM_REQUEST, request)
      .executionStrategy(request.getExecutionStrategy())
      .build();
  }
}
