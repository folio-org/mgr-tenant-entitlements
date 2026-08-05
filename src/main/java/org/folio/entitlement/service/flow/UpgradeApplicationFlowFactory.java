package org.folio.entitlement.service.flow;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.service.stage.ApplicationDependencyUpdater;
import org.folio.entitlement.service.stage.ApplicationDiscoveryLoader;
import org.folio.entitlement.service.stage.ApplicationFlowInitializer;
import org.folio.entitlement.service.stage.UpgradeApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.UpgradeRequestDependencyValidator;
import org.folio.flow.api.DynamicStage;
import org.folio.flow.api.Flow;
import org.folio.flow.model.FlowExecutionStrategy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpgradeApplicationFlowFactory implements ApplicationFlowFactory {

  private final ApplicationDependencyUpdater applicationDependencyUpdater;
  private final UpgradeRequestDependencyValidator upgradeRequestDependencyValidator;

  private final ModulesFlowProvider modulesFlowProvider;
  private final ApplicationDiscoveryLoader applicationDiscoveryLoader;

  private final ApplicationFlowInitializer flowInitializer;
  private final UpgradeApplicationFlowFinalizer finishedFlowFinalizer;
  private final ApplicationFlowFinalizerCallbacks finalizerCallbacks;

  @Override
  public Flow createFlow(Object flowId, FlowExecutionStrategy strategy, Map<?, ?> additionalFlowParameter) {
    var builder = Flow.builder()
      .id(flowId)
      .stage(flowInitializer)
      .stage(upgradeRequestDependencyValidator)
      .stage(applicationDiscoveryLoader)
      .stage(DynamicStage.of(modulesFlowProvider.getName(), modulesFlowProvider::createFlow))
      .stage(applicationDependencyUpdater)
      .stage(finishedFlowFinalizer);

    return finalizerCallbacks.apply(builder)
      .executionStrategy(strategy)
      .flowParameters(additionalFlowParameter)
      .build();
  }

  @Override
  public EntitlementType getEntitlementType() {
    return EntitlementType.UPGRADE;
  }
}
