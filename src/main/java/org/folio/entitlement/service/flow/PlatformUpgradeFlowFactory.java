package org.folio.entitlement.service.flow;

import static org.folio.entitlement.domain.model.PlatformStageContext.PARAM_PLATFORM_REQUEST;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.PlatformEntitlementRequest;
import org.folio.entitlement.service.validator.PlatformDependentsValidator;
import org.folio.entitlement.service.validator.PlatformScopeValidator;
import org.folio.entitlement.service.validator.PlatformUpgradeValidator;
import org.folio.flow.api.Flow;
import org.folio.flow.model.FlowExecutionStrategy;
import org.springframework.stereotype.Component;

/**
 * Builds the flow that replaces the entitled version of a platform application with a higher one.
 */
@Component
@RequiredArgsConstructor
public class PlatformUpgradeFlowFactory implements PlatformFlowFactory {

  private final PlatformScopeValidator scopeValidator;
  private final PlatformUpgradeValidator upgradeValidator;
  private final PlatformDependentsValidator dependentsValidator;
  private final PlatformEntitlementCommitStage commitStage;

  @Override
  public Flow createFlow(UUID flowId, PlatformEntitlementRequest request) {
    return Flow.builder()
      .id(flowId)
      .flowParameter(PARAM_PLATFORM_REQUEST, request)
      .executionStrategy(FlowExecutionStrategy.CANCEL_ON_ERROR)
      .stage(scopeValidator)
      .stage(upgradeValidator)
      .stage(dependentsValidator)
      .stage(commitStage)
      .build();
  }
}
