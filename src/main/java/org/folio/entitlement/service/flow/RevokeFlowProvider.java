package org.folio.entitlement.service.flow;

import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowProviderUtils.combineStages;
import static org.folio.flow.api.NoOpStage.noOpStage;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.folio.entitlement.integration.folio.ModuleInstallationFlowProvider;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCleaner;
import org.folio.entitlement.integration.kong.KongRouteCleaner;
import org.folio.entitlement.integration.okapi.OkapiModuleInstallerFlowProvider;
import org.folio.entitlement.service.stage.ApplicationDependencyCleaner;
import org.folio.entitlement.service.stage.ApplicationDescriptorLoader;
import org.folio.entitlement.service.stage.ApplicationDiscoveryLoader;
import org.folio.entitlement.service.stage.EntitlementDependencyValidator;
import org.folio.entitlement.service.stage.EntitlementFlowInitializer;
import org.folio.entitlement.service.stage.FailedFlowFinalizer;
import org.folio.entitlement.service.stage.RevokeFlowFinalizer;
import org.folio.entitlement.service.stage.TenantLoader;
import org.folio.flow.api.DynamicStage;
import org.folio.flow.api.Flow;
import org.folio.flow.api.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RevokeFlowProvider {

  private final TenantLoader tenantLoader;
  private final ApplicationDescriptorLoader applicationDescriptorLoader;
  private final ApplicationDiscoveryLoader applicationDiscoveryLoader;
  private final ApplicationDependencyCleaner applicationDependencyCleaner;
  private final EntitlementDependencyValidator entitlementDependencyValidator;
  private final RevokeFlowFinalizer revokeFlowFinalizer;
  private final EntitlementFlowInitializer entitlementFlowInitializer;
  private final FailedFlowFinalizer failedFlowFinalizer;

  @Setter(onMethod_ = @Autowired(required = false))
  private KongRouteCleaner kongRouteCleaner;

  @Setter(onMethod_ = @Autowired(required = false))
  private KeycloakAuthResourceCleaner keycloakAuthResourceCleaner;

  @Setter(onMethod_ = @Autowired(required = false))
  private OkapiModuleInstallerFlowProvider okapiModuleInstallerFlowProvider;

  @Setter(onMethod_ = @Autowired(required = false))
  private ModuleInstallationFlowProvider folioModuleInstallerFlowProvider;

  /**
   * Creates a {@link Flow} object for application installation.
   *
   * @param applicationFlowId - application flow identifier as {@link UUID} object
   * @param applicationId - application identifier as {@link String} object
   * @return created {@link Flow} for future execution
   */
  public Flow prepareFlow(Object applicationFlowId, String applicationId) {
    return Flow.builder()
      .id(applicationFlowId)
      .executionStrategy(IGNORE_ON_ERROR)
      .flowParameter(PARAM_APP_ID, applicationId)
      .stage(entitlementFlowInitializer)
      .stage(tenantLoader)
      .stage(applicationDescriptorLoader)
      .stage(entitlementDependencyValidator)
      .stage(applicationDiscoveryLoader)
      .stage(Flow.builder()
        .id(applicationFlowId + "/module-uninstaller")
        .executionStrategy(IGNORE_ON_ERROR)
        .stage(getModuleInstallerStage())
        .stage(combineStages(kongRouteCleaner, keycloakAuthResourceCleaner))
        .build())
      .stage(applicationDependencyCleaner)
      .stage(revokeFlowFinalizer)
      .onFlowError(failedFlowFinalizer)
      .build();
  }

  private Stage getModuleInstallerStage() {
    if (okapiModuleInstallerFlowProvider != null) {
      return DynamicStage.of(okapiModuleInstallerFlowProvider::prepareFlow);
    }

    if (folioModuleInstallerFlowProvider != null) {
      return DynamicStage.of(folioModuleInstallerFlowProvider::prepareFlow);
    }

    return noOpStage();
  }
}
