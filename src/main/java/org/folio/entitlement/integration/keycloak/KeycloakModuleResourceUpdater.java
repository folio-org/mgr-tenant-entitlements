package org.folio.entitlement.integration.keycloak;

import static org.apache.commons.lang3.StringUtils.uncapitalize;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.keycloak.admin.client.Keycloak;

@RequiredArgsConstructor
public class KeycloakModuleResourceUpdater extends DatabaseLoggingStage<ModuleStageContext> {

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

  @Override
  public String getStageName(ModuleStageContext context) {
    return context.getModuleId() + "-" + uncapitalize(this.getClass().getSimpleName());
  }
}
