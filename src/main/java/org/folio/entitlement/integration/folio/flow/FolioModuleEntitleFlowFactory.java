package org.folio.entitlement.integration.folio.flow;

import static java.util.Arrays.asList;
import static org.folio.entitlement.utils.FlowUtils.combineStages;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.integration.folio.stage.FolioModuleEventPublisher;
import org.folio.entitlement.integration.folio.stage.FolioModuleInstaller;
import org.folio.entitlement.integration.kafka.CapabilitiesModuleEventPublisher;
import org.folio.entitlement.integration.kafka.ScheduledJobModuleEventPublisher;
import org.folio.entitlement.integration.kafka.SystemUserModuleEventPublisher;
import org.folio.entitlement.integration.keycloak.KeycloakModuleResourceCreator;
import org.folio.entitlement.integration.kong.KongModuleRouteCreator;
import org.folio.entitlement.service.flow.ModuleFlowFactory;
import org.folio.flow.api.Flow;
import org.folio.flow.model.FlowExecutionStrategy;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor
public class FolioModuleEntitleFlowFactory implements ModuleFlowFactory {

  private final FolioModuleInstaller folioModuleInstaller;
  private final FolioModuleEventPublisher folioModuleEventPublisher;
  private final SystemUserModuleEventPublisher systemUserEventPublisher;
  private final ScheduledJobModuleEventPublisher scheduledJobEventPublisher;
  private final CapabilitiesModuleEventPublisher capabilitiesEventPublisher;

  private KongModuleRouteCreator kongModuleRouteCreator;
  private KeycloakModuleResourceCreator kcModuleResourceCreator;

  @Override
  public Flow createModuleFlow(Object flowId, FlowExecutionStrategy strategy, Map<?, ?> additionalFlowParameters) {
    return Flow.builder()
      .id(flowId)
      .stage(combineStages("ResourceCreatorParallelStage", asList(kongModuleRouteCreator, kcModuleResourceCreator)))
      .stage(folioModuleInstaller)
      .stage(folioModuleEventPublisher)
      .stage(combineStages("EventPublishingParallelStage", asList(
        systemUserEventPublisher, scheduledJobEventPublisher, capabilitiesEventPublisher)))
      .executionStrategy(strategy)
      .flowParameters(additionalFlowParameters)
      .build();
  }

  @Override
  public Flow createUiModuleFlow(Object flowId, FlowExecutionStrategy strategy, Map<?, ?> additionalFlowParameters) {
    return Flow.builder()
      .id(flowId)
      .stage(capabilitiesEventPublisher)
      .executionStrategy(strategy)
      .flowParameters(additionalFlowParameters)
      .build();
  }

  @Override
  public EntitlementType getEntitlementType() {
    return EntitlementType.ENTITLE;
  }

  @Autowired(required = false)
  public void setKongModuleRouteCreator(KongModuleRouteCreator kongModuleRouteCreator) {
    this.kongModuleRouteCreator = kongModuleRouteCreator;
  }

  @Autowired(required = false)
  public void setKcModuleResourceCreator(KeycloakModuleResourceCreator kcModuleResourceCreator) {
    this.kcModuleResourceCreator = kcModuleResourceCreator;
  }
}
