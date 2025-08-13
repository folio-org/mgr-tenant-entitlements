package org.folio.entitlement.service.flow;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.service.stage.ApplicationDependencySaver;
import org.folio.entitlement.service.stage.ApplicationDescriptorValidator;
import org.folio.entitlement.service.stage.ApplicationDiscoveryLoader;
import org.folio.entitlement.service.stage.ApplicationFlowInitializer;
import org.folio.entitlement.service.stage.CancellationFailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.CancelledApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.EntitleApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.EntitleRequestDependencyValidator;
import org.folio.entitlement.service.stage.FailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.SkippedApplicationFlowFinalizer;
import org.folio.flow.api.DynamicStage;
import org.folio.flow.api.Flow;
import org.folio.flow.model.FlowExecutionStrategy;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntitleApplicationFlowFactory implements ApplicationFlowFactory {

  private final ApplicationDescriptorValidator applicationDescriptorValidator;
  private final ApplicationDependencySaver applicationDependencySaver;
  private final ApplicationDiscoveryLoader applicationDiscoveryLoader;
  private final EntitleRequestDependencyValidator entitleRequestDependencyValidator;

  private final ModulesFlowProvider modulesFlowFactory;
  private final ApplicationFlowInitializer flowInitializer;
  private final FailedApplicationFlowFinalizer failedFlowFinalizer;
  private final SkippedApplicationFlowFinalizer skippedFlowFinalizer;
  private final EntitleApplicationFlowFinalizer finishedFlowFinalizer;
  private final CancelledApplicationFlowFinalizer cancelledFlowFinalizer;
  private final CancellationFailedApplicationFlowFinalizer cancellationFailedFlowFinalizer;

  /**
   * Creates a {@link Flow} object for application installation.
   *
   * @param flowId - application flow identifier as {@link UUID} object
   * @param strategy - flow execution strategy as {@link FlowExecutionStrategy} enum value
   * @return created {@link Flow} for future execution
   */
  @Override
  public Flow createFlow(Object flowId, FlowExecutionStrategy strategy, Map<?, ?> additionalFlowParameters) {
    return Flow.builder()
      .id(flowId)
      .stage(flowInitializer)
      .stage(applicationDescriptorValidator)
      .stage(applicationDependencySaver)
      .stage(entitleRequestDependencyValidator)
      .stage(applicationDiscoveryLoader)
      .stage(DynamicStage.of(modulesFlowFactory.getName(), modulesFlowFactory::createFlow))
      .stage(finishedFlowFinalizer)
      .onFlowSkip(skippedFlowFinalizer)
      .onFlowError(failedFlowFinalizer)
      .onFlowCancellation(cancelledFlowFinalizer)
      .onFlowCancellationError(cancellationFailedFlowFinalizer)
      .executionStrategy(strategy)
      .flowParameters(additionalFlowParameters)
      .build();
  }

  @Override
  public EntitlementType getEntitlementType() {
    return EntitlementType.ENTITLE;
  }
}
