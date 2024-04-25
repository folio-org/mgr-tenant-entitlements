package org.folio.entitlement.integration.keycloak;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.keycloak.admin.client.Keycloak;

@RequiredArgsConstructor
public class KeycloakAuthResourceCleaner extends DatabaseLoggingStage<ApplicationStageContext> {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;

  @Override
  public void execute(ApplicationStageContext context) {
    var request = context.getEntitlementRequest();
    if (!request.isPurge()) {
      return;
    }

    var realm = context.<String>get(PARAM_TENANT_NAME);
    keycloakClient.tokenManager().grantToken();
    var applicationDescriptor = context.getApplicationDescriptor();
    var moduleDescriptors = applicationDescriptor.getModuleDescriptors();
    emptyIfNull(moduleDescriptors).forEach(descriptor -> keycloakService.removeAuthResources(descriptor, realm));
  }
}
