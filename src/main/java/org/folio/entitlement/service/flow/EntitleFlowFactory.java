package org.folio.entitlement.service.flow;

import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.kafka.KafkaTenantTopicCreator;
import org.folio.entitlement.service.stage.ApplicationDescriptorTreeLoader;
import org.folio.entitlement.service.stage.CancellationFailedFlowFinalizer;
import org.folio.entitlement.service.stage.CancelledFlowFinalizer;
import org.folio.entitlement.service.stage.FailedFlowFinalizer;
import org.folio.entitlement.service.stage.FinishedFlowFinalizer;
import org.folio.entitlement.service.stage.FlowInitializer;
import org.folio.entitlement.service.stage.TenantLoader;
import org.folio.entitlement.service.validator.ApplicationFlowValidator;
import org.folio.entitlement.service.validator.StageRequestValidator;
import org.folio.flow.api.DynamicStage;
import org.folio.flow.api.Flow;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EntitleFlowFactory implements FlowFactory {

  private final TenantLoader tenantLoader;
  private final BaseApplicationsFlowProvider applicationsFlowFactory;
  private final ApplicationFlowValidator applicationFlowValidator;
  @Qualifier("entitlementInterfaceIntegrityValidator")
  private final StageRequestValidator interfaceIntegrityValidator;
  private final ApplicationFlowQueuingStage applicationFlowQueuingStage;
  private final ApplicationDescriptorTreeLoader applicationDescriptorTreeLoader;
  private final KafkaTenantTopicCreator kafkaTenantTopicCreator;

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
      .stage(applicationFlowValidator)
      .stage(tenantLoader)
      .stage(applicationDescriptorTreeLoader)
      .stage(interfaceIntegrityValidator)
      .stage(applicationFlowQueuingStage)
      .stage(kafkaTenantTopicCreator)
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
