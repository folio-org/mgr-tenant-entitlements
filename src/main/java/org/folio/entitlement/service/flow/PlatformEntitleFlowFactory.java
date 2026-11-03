package org.folio.entitlement.service.flow;

import static org.folio.entitlement.domain.model.PlatformStageContext.PARAM_PLATFORM_REQUEST;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.PlatformEntitlementRequest;
import org.folio.entitlement.service.validator.PlatformEntitleValidator;
import org.folio.entitlement.service.validator.PlatformScopeValidator;
import org.folio.flow.api.Flow;
import org.folio.flow.model.FlowExecutionStrategy;
import org.springframework.stereotype.Component;

/**
 * Builds the flow that entitles a platform application for the first time.
 */
@Component
@RequiredArgsConstructor
public class PlatformEntitleFlowFactory implements PlatformFlowFactory {

  private final PlatformScopeValidator scopeValidator;
  private final PlatformEntitleValidator entitleValidator;
  private final PlatformEntitlementCommitStage commitStage;

  @Override
  public Flow createFlow(UUID flowId, PlatformEntitlementRequest request) {
    return Flow.builder()
      .id(flowId)
      .flowParameter(PARAM_PLATFORM_REQUEST, request)
      .executionStrategy(FlowExecutionStrategy.CANCEL_ON_ERROR)
      .stage(scopeValidator)
      .stage(entitleValidator)
      .stage(commitStage)
      .build();
  }
}
