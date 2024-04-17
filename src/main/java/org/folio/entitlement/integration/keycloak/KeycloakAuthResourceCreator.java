package org.folio.entitlement.integration.keycloak;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.folio.ApplicationStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.keycloak.admin.client.Keycloak;

@RequiredArgsConstructor
public class KeycloakAuthResourceCreator extends DatabaseLoggingStage<ApplicationStageContext> {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;

  @Override
  public void execute(ApplicationStageContext context) {
    var realm = context.getTenantName();
    var applicationDescriptor = context.getApplicationDescriptor();
    var moduleDescriptors = applicationDescriptor.getModuleDescriptors();
    keycloakClient.tokenManager().grantToken();
    emptyIfNull(moduleDescriptors).forEach(desc -> keycloakService.updateAuthResources(null, desc, realm));
  }

  @Override
  public void cancel(ApplicationStageContext context) {
    var realm = context.getTenantName();
    var applicationDescriptor = context.getApplicationDescriptor();
    var moduleDescriptors = applicationDescriptor.getModuleDescriptors();
    keycloakClient.tokenManager().grantToken();
    emptyIfNull(moduleDescriptors).forEach(descriptor -> keycloakService.removeAuthResources(descriptor, realm));
  }

  @Override
  public boolean shouldCancelIfFailed(ApplicationStageContext context) {
    return true;
  }
}
