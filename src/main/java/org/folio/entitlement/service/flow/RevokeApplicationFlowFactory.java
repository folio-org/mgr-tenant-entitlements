package org.folio.entitlement.service.flow;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.service.stage.ApplicationDependencyCleaner;
import org.folio.entitlement.service.stage.ApplicationDiscoveryLoader;
import org.folio.entitlement.service.stage.ApplicationFlowInitializer;
import org.folio.entitlement.service.stage.FailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.RevokeApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.RevokeRequestDependencyValidator;
import org.folio.entitlement.service.stage.SkippedApplicationFlowFinalizer;
import org.folio.flow.api.DynamicStage;
import org.folio.flow.api.Flow;
import org.folio.flow.model.FlowExecutionStrategy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RevokeApplicationFlowFactory implements ApplicationFlowFactory {

  private final ApplicationDiscoveryLoader applicationDiscoveryLoader;
  private final ApplicationDependencyCleaner applicationDependencyCleaner;
  private final RevokeRequestDependencyValidator requestDependencyValidator;

  private final ApplicationFlowInitializer flowInitializer;
  private final FailedApplicationFlowFinalizer failedFlowFinalizer;
  private final RevokeApplicationFlowFinalizer finishedFlowFinalizer;
  private final SkippedApplicationFlowFinalizer skippedFlowFinalizer;
  private final ModulesFlowProvider modulesFlowProvider;

  @Override
  public Flow createFlow(Object flowId, FlowExecutionStrategy strategy, Map<?, ?> additionalFlowParameter) {
    return Flow.builder()
      .id(flowId)
      .stage(flowInitializer)
      .stage(requestDependencyValidator)
      .stage(applicationDiscoveryLoader)
      .stage(DynamicStage.of(modulesFlowProvider.getName(), modulesFlowProvider::createFlow))
      .stage(applicationDependencyCleaner)
      .stage(finishedFlowFinalizer)
      .onFlowSkip(skippedFlowFinalizer)
      .onFlowError(failedFlowFinalizer)
      .executionStrategy(strategy)
      .flowParameters(additionalFlowParameter)
      .build();
  }

  @Override
  public EntitlementType getEntitlementType() {
    return EntitlementType.REVOKE;
  }
}
