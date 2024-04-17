package org.folio.entitlement.service.flow;

import static java.util.Arrays.asList;
import static org.folio.entitlement.utils.FlowUtils.combineStages;
import static org.folio.flow.api.NoOpStage.noOpStage;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.folio.entitlement.integration.folio.ModuleInstallationFlowProvider;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCleaner;
import org.folio.entitlement.integration.kong.KongRouteCleaner;
import org.folio.entitlement.integration.okapi.OkapiModuleInstallerFlowProvider;
import org.folio.entitlement.service.stage.ApplicationDependencyCleaner;
import org.folio.entitlement.service.stage.ApplicationDiscoveryLoader;
import org.folio.entitlement.service.stage.ApplicationFlowInitializer;
import org.folio.entitlement.service.stage.FailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.RevokeApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.RevokeRequestDependencyValidator;
import org.folio.entitlement.service.stage.SkippedApplicationFlowFinalizer;
import org.folio.flow.api.DynamicStage;
import org.folio.flow.api.Flow;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;
import org.folio.flow.model.FlowExecutionStrategy;
import org.springframework.beans.factory.annotation.Autowired;
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

  @Setter(onMethod_ = @Autowired(required = false))
  private ModuleInstallationFlowProvider folioModuleInstallerFlowProvider;

  @Setter(onMethod_ = @Autowired(required = false))
  private KongRouteCleaner kongRouteCleaner;

  @Setter(onMethod_ = @Autowired(required = false))
  private KeycloakAuthResourceCleaner keycloakAuthResourceCleaner;

  @Setter(onMethod_ = @Autowired(required = false))
  private OkapiModuleInstallerFlowProvider okapiModuleInstallerFlowProvider;

  @Override
  public Flow createFlow(Object flowId, FlowExecutionStrategy strategy, Map<?, ?> additionalFlowParameter) {
    return Flow.builder()
      .id(flowId)
      .stage(flowInitializer)
      .stage(requestDependencyValidator)
      .stage(applicationDiscoveryLoader)
      .stage(Flow.builder()
        .id(flowId + "/ModuleUninstaller")
        .executionStrategy(IGNORE_ON_ERROR)
        .stage(getModuleInstallerStage())
        .stage(combineStages("ParallelResourcesCleaner", asList(kongRouteCleaner, keycloakAuthResourceCleaner)))
        .build())
      .stage(applicationDependencyCleaner)
      .stage(finishedFlowFinalizer)
      .onFlowSkip(skippedFlowFinalizer)
      .onFlowError(failedFlowFinalizer)
      .executionStrategy(strategy)
      .flowParameters(additionalFlowParameter)
      .build();
  }

  private Stage<StageContext> getModuleInstallerStage() {
    if (okapiModuleInstallerFlowProvider != null) {
      return DynamicStage.of("OkapiModuleUninstallerProvider", okapiModuleInstallerFlowProvider::prepareFlow);
    }

    if (folioModuleInstallerFlowProvider != null) {
      return DynamicStage.of("FolioModuleUninstallerProvider", folioModuleInstallerFlowProvider::prepareFlow);
    }

    return noOpStage();
  }
}
