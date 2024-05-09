package org.folio.entitlement.integration.keycloak;

import static org.folio.entitlement.utils.EntitlementServiceUtils.isModuleVersionChanged;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;
import org.keycloak.admin.client.Keycloak;

@RequiredArgsConstructor
public class KeycloakModuleResourceUpdater extends ModuleDatabaseLoggingStage {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;

  @Override
  public void execute(ModuleStageContext context) {
    var tenantName = context.getTenantName();
    var moduleDescriptor = context.getModuleDescriptor();
    var entitledModuleDescriptor = context.getInstalledModuleDescriptor();

    if (isModuleVersionChanged(moduleDescriptor, entitledModuleDescriptor)) {
      keycloakClient.tokenManager().grantToken();
      keycloakService.updateAuthResources(entitledModuleDescriptor, moduleDescriptor, tenantName);
    }
  }
}
