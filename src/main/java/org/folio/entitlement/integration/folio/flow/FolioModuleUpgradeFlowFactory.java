package org.folio.entitlement.integration.folio.flow;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.folio.entitlement.utils.FlowUtils.combineStages;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.integration.kafka.ScheduledJobModuleEventPublisher;
import org.folio.entitlement.integration.keycloak.KeycloakModuleResourceUpdater;
import org.folio.entitlement.integration.kong.KongModuleRouteUpdater;
import org.folio.entitlement.service.flow.ModuleFlowFactory;
import org.folio.flow.api.Flow;
import org.folio.flow.model.FlowExecutionStrategy;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor
public class FolioModuleUpgradeFlowFactory implements ModuleFlowFactory {

  private final ScheduledJobModuleEventPublisher scheduledJobEventPublisher;
  private KongModuleRouteUpdater kongModuleRouteUpdater;
  private KeycloakModuleResourceUpdater kcModuleResourceUpdater;

  @Override
  public Flow createModuleFlow(Object flowId, FlowExecutionStrategy strategy, Map<?, ?> additionalFlowParameters) {
    return Flow.builder()
      .id(flowId)
      .executionStrategy(strategy)
      .stage(combineStages("ResourceUpdaterParallelStage", asList(kongModuleRouteUpdater, kcModuleResourceUpdater)))
      .stage(combineStages("EventPublishingParallelStage", singletonList(scheduledJobEventPublisher)))
      .flowParameters(additionalFlowParameters)
      .build();
  }

  @Override
  public Flow createUiModuleFlow(Object flowId, FlowExecutionStrategy strategy, Map<?, ?> additionalFlowParameters) {
    return Flow.builder()
      .id(flowId)
      .executionStrategy(strategy)
      .flowParameters(additionalFlowParameters)
      .build();
  }

  @Override
  public EntitlementType getEntitlementType() {
    return EntitlementType.UPGRADE;
  }

  @Autowired(required = false)
  public void setKongModuleRouteUpdater(KongModuleRouteUpdater kongModuleRouteUpdater) {
    this.kongModuleRouteUpdater = kongModuleRouteUpdater;
  }

  @Autowired(required = false)
  public void setKcModuleResourceUpdater(KeycloakModuleResourceUpdater kcModuleResourceUpdater) {
    this.kcModuleResourceUpdater = kcModuleResourceUpdater;
  }
}
