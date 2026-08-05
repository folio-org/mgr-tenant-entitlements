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
import org.folio.entitlement.service.validator.StageRequestValidator;
import org.folio.entitlement.service.validator.UpgradeRequestValidator;
import org.folio.flow.api.DynamicStage;
import org.folio.flow.api.Flow;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpgradeFlowFactory implements FlowFactory {

  private final TenantLoader tenantLoader;
  private final BaseApplicationsFlowProvider applicationsFlowFactory;
  private final UpgradeRequestValidator upgradeRequestValidator;
  private final ApplicationFlowValidator applicationFlowValidator;
  @Qualifier("upgradeInterfaceIntegrityValidator")
  private final StageRequestValidator interfaceIntegrityValidator;
  private final ApplicationFlowQueuingStage applicationFlowQueuingStage;
  private final ApplicationDescriptorLoader applicationDescriptorLoader;

  private final FinishedFlowFinalizer finishedFlowFinalizer;
  private final FlowInitializer flowInitializer;
  private final FlowFinalizerCallbacks finalizerCallbacks;

  @Override
  public Flow createFlow(EntitlementRequest request) {
    var builder = Flow.builder()
      .id(UUID.randomUUID())
      .stage(flowInitializer)
      .stage(upgradeRequestValidator)
      .stage(applicationFlowValidator)
      .stage(tenantLoader)
      .stage(applicationDescriptorLoader)
      .stage(interfaceIntegrityValidator)
      .stage(applicationFlowQueuingStage)
      .stage(DynamicStage.of(applicationsFlowFactory.getName(), applicationsFlowFactory::createFlow))
      .stage(finishedFlowFinalizer);

    return finalizerCallbacks.apply(builder)
      .flowParameter(PARAM_REQUEST, request)
      .executionStrategy(request.getExecutionStrategy())
      .build();
  }
}
