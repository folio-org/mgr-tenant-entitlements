package org.folio.entitlement.service.flow;

import static java.util.Collections.singletonList;
import static org.folio.entitlement.utils.FlowUtils.combineStages;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceUpdater;
import org.folio.entitlement.service.stage.ApplicationDependencyUpdater;
import org.folio.entitlement.service.stage.ApplicationFlowInitializer;
import org.folio.entitlement.service.stage.FailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.SkippedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.UpgradeApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.UpgradeRequestDependencyValidator;
import org.folio.flow.api.Flow;
import org.folio.flow.model.FlowExecutionStrategy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UpgradeApplicationFlowFactory implements ApplicationFlowFactory {

  private final ApplicationDependencyUpdater applicationDependencyUpdater;
  private final UpgradeRequestDependencyValidator requestDependencyValidator;

  private final ApplicationFlowInitializer flowInitializer;
  private final FailedApplicationFlowFinalizer failedFlowFinalizer;
  private final UpgradeApplicationFlowFinalizer finishedFlowFinalizer;
  private final SkippedApplicationFlowFinalizer skippedFlowFinalizer;

  @Setter(onMethod_ = @Autowired(required = false))
  private KeycloakAuthResourceUpdater keycloakAuthResourceUpdater;

  @Override
  public Flow createFlow(Object flowId, FlowExecutionStrategy strategy, Map<?, ?> additionalFlowParameter) {
    return Flow.builder()
      .id(flowId)
      .stage(flowInitializer)
      .stage(requestDependencyValidator)
      .stage(Flow.builder()
        .id(flowId + "/ModuleUpdater")
        .stage(combineStages("ParallelResourcesUpdater", singletonList(keycloakAuthResourceUpdater)))
        .executionStrategy(IGNORE_ON_ERROR)
        .build())
      .stage(applicationDependencyUpdater)
      .stage(finishedFlowFinalizer)
      .onFlowSkip(skippedFlowFinalizer)
      .onFlowError(failedFlowFinalizer)
      .executionStrategy(strategy)
      .flowParameters(additionalFlowParameter)
      .build();
  }
}
