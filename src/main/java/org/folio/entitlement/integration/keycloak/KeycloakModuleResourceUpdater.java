package org.folio.entitlement.integration.keycloak;

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

    keycloakClient.tokenManager().grantToken();

    var entitledModuleDescriptor = context.getInstalledModuleDescriptor();
    var moduleDescriptor = context.getModuleDescriptor();
    keycloakService.updateAuthResources(entitledModuleDescriptor, moduleDescriptor, tenantName);
  }
}
