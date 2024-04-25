package org.folio.entitlement.integration.keycloak;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.keycloak.admin.client.Keycloak;

@Log4j2
@RequiredArgsConstructor
public class KeycloakAuthResourceCreator extends DatabaseLoggingStage<ApplicationStageContext> {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;

  @Override
  public void execute(ApplicationStageContext ctx) {
    var realm = ctx.getTenantName();
    var applicationDescriptor = ctx.getApplicationDescriptor();
    var moduleDescriptors = applicationDescriptor.getModuleDescriptors();
    keycloakClient.tokenManager().grantToken();
    emptyIfNull(moduleDescriptors).forEach(desc -> keycloakService.updateAuthResources(null, desc, realm));
  }

  @Override
  public void cancel(ApplicationStageContext ctx) {
    var request = ctx.getEntitlementRequest();
    if (!request.isPurgeOnRollback()) {
      log.debug("Skipping purge of Keycloak resource during rollback: applicationId = {}",  ctx.getApplicationId());
      return;
    }

    var realm = ctx.getTenantName();
    var applicationDescriptor = ctx.getApplicationDescriptor();
    var moduleDescriptors = applicationDescriptor.getModuleDescriptors();

    keycloakClient.tokenManager().grantToken();
    emptyIfNull(moduleDescriptors).forEach(descriptor -> keycloakService.removeAuthResources(descriptor, realm));
  }

  @Override
  public boolean shouldCancelIfFailed(ApplicationStageContext context) {
    return true;
  }
}
