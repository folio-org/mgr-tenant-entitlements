package org.folio.entitlement.integration.folio.flow;

import static java.util.Arrays.asList;
import static org.folio.entitlement.utils.FlowUtils.combineStages;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.integration.folio.stage.FolioModuleEventPublisher;
import org.folio.entitlement.integration.folio.stage.FolioModuleUninstaller;
import org.folio.entitlement.integration.keycloak.KeycloakModuleResourceCleaner;
import org.folio.entitlement.integration.kong.KongModuleRouteCleaner;
import org.folio.entitlement.service.flow.ModuleFlowFactory;
import org.folio.flow.api.Flow;
import org.folio.flow.model.FlowExecutionStrategy;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor
public class FolioModuleRevokeFlowFactory implements ModuleFlowFactory {

  private final FolioModuleUninstaller folioModuleUninstaller;
  private final FolioModuleEventPublisher folioModuleEventPublisher;

  private KongModuleRouteCleaner kongModuleRouteCleaner;
  private KeycloakModuleResourceCleaner kcModuleResourceCleaner;

  @Override
  public Flow createModuleFlow(Object flowId, FlowExecutionStrategy strategy, Map<?, ?> additionalFlowParameters) {
    return Flow.builder()
      .id(flowId)
      .stage(folioModuleUninstaller)
      .stage(folioModuleEventPublisher)
      .stage(combineStages("ResourcesCleanerParallelStage", asList(kongModuleRouteCleaner, kcModuleResourceCleaner)))
      .executionStrategy(strategy)
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
    return EntitlementType.REVOKE;
  }

  @Autowired(required = false)
  public void setKongModuleRouteCleaner(KongModuleRouteCleaner kongModuleRouteCleaner) {
    this.kongModuleRouteCleaner = kongModuleRouteCleaner;
  }

  @Autowired(required = false)
  public void setKcModuleResourceCleaner(KeycloakModuleResourceCleaner kcModuleResourceCleaner) {
    this.kcModuleResourceCleaner = kcModuleResourceCleaner;
  }
}
