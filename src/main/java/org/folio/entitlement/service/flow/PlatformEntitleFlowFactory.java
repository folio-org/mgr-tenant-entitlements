package org.folio.entitlement.service.flow;

import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.PlatformEntitlementRequest;
import org.folio.entitlement.service.stage.FinishedFlowFinalizer;
import org.folio.entitlement.service.stage.FlowInitializer;
import org.folio.entitlement.service.validator.PlatformApplicationScopeValidator;
import org.folio.entitlement.service.validator.PlatformExistEntitlementValidator;
import org.folio.flow.api.Flow;
import org.folio.flow.model.FlowExecutionStrategy;
import org.springframework.stereotype.Component;

/**
 * Builds the flow that entitles a platform application for the first time.
 */
@Component
@RequiredArgsConstructor
public class PlatformEntitleFlowFactory implements PlatformFlowFactory {

  private final FlowInitializer flowInitializer;
  private final PlatformApplicationScopeValidator applicationScopeValidator;
  private final PlatformExistEntitlementValidator existEntitlementValidator;
  private final PlatformEntitlementCommitStage commitStage;

  private final FinishedFlowFinalizer finishedFlowFinalizer;
  private final FlowFinalizerCallbacks finalizerCallbacks;

  @Override
  public Flow createFlow(UUID flowId, PlatformEntitlementRequest request) {
    var builder = Flow.builder()
      .id(flowId)
      .stage(flowInitializer)
      .stage(applicationScopeValidator)
      .stage(existEntitlementValidator)
      .stage(commitStage)
      .stage(finishedFlowFinalizer);
    return finalizerCallbacks.apply(builder)
      .flowParameter(PARAM_REQUEST, request.toEntitlementRequest())
      .executionStrategy(FlowExecutionStrategy.IGNORE_ON_ERROR)
      .build();
  }
}
