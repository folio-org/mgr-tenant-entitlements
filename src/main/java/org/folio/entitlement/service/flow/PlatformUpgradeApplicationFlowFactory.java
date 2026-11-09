package org.folio.entitlement.service.flow;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.service.stage.ApplicationDescriptorValidator;
import org.folio.entitlement.service.stage.ApplicationFlowInitializer;
import org.folio.entitlement.service.stage.PlatformApplicationDependencyUpdater;
import org.folio.entitlement.service.stage.PlatformDependencyValidator;
import org.folio.entitlement.service.stage.PlatformUpgradeApplicationFlowFinalizer;
import org.folio.flow.api.Flow;
import org.folio.flow.model.FlowExecutionStrategy;
import org.springframework.stereotype.Component;

/**
 * Builds the flow that replaces the entitled version of one platform application. Modules are not installed: the
 * platform entitlement is deployment-wide and has no tenant.
 */
@Component
@RequiredArgsConstructor
public class PlatformUpgradeApplicationFlowFactory implements ApplicationFlowFactory {

  private final ApplicationFlowInitializer flowInitializer;
  private final ApplicationDescriptorValidator applicationDescriptorValidator;
  private final PlatformDependencyValidator dependencyValidator;
  private final PlatformApplicationDependencyUpdater dependencyUpdater;
  private final PlatformUpgradeApplicationFlowFinalizer finishedFlowFinalizer;
  private final ApplicationFlowFinalizerCallbacks finalizerCallbacks;

  @Override
  public Flow createFlow(Object flowId, FlowExecutionStrategy strategy, Map<?, ?> additionalFlowParameters) {
    var builder = Flow.builder()
      .id(flowId)
      .stage(flowInitializer)
      .stage(applicationDescriptorValidator)
      .stage(dependencyValidator)
      .stage(dependencyUpdater)
      .stage(finishedFlowFinalizer);

    return finalizerCallbacks.apply(builder)
      .executionStrategy(strategy)
      .flowParameters(additionalFlowParameters)
      .build();
  }

  @Override
  public EntitlementType getEntitlementType() {
    return EntitlementType.PLATFORM_UPGRADE;
  }
}
