package org.folio.entitlement.integration.keycloak;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.keycloak.admin.client.Keycloak;

@Log4j2
@RequiredArgsConstructor
public class KeycloakModuleResourceCreator extends DatabaseLoggingStage<ModuleStageContext> {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;

  @Override
  public void execute(ModuleStageContext context) {
    var realm = context.getTenantName();
    keycloakClient.tokenManager().grantToken();
    keycloakService.updateAuthResources(null, context.getModuleDescriptor(), realm);
  }

  @Override
  public void cancel(ModuleStageContext context) {
    var request = context.getEntitlementRequest();
    var moduleDescriptor = context.getModuleDescriptor();
    if (!request.isPurgeOnRollback()) {
      log.debug("Skipping purge of Keycloak resources during rollback: moduleId = {}",  context.getModuleId());
      return;
    }

    var tenantName = context.getTenantName();
    keycloakClient.tokenManager().grantToken();
    keycloakService.removeAuthResources(moduleDescriptor, tenantName);
  }

  @Override
  public boolean shouldCancelIfFailed(ModuleStageContext context) {
    return true;
  }
}
