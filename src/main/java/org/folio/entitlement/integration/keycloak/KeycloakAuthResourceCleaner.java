package org.folio.entitlement.integration.keycloak;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.okapi.model.OkapiStageContext;
import org.folio.entitlement.retry.annotations.KeycloakCallsRetryable;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.keycloak.admin.client.Keycloak;

@RequiredArgsConstructor
@KeycloakCallsRetryable
public class KeycloakAuthResourceCleaner extends DatabaseLoggingStage<OkapiStageContext> {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;

  @Override
  public void execute(OkapiStageContext context) {
    var request = context.getEntitlementRequest();
    if (!request.isPurge()) {
      return;
    }

    var realm = context.getTenantName();
    keycloakClient.tokenManager().grantToken();
    for (var moduleDescriptor : context.getModuleDescriptors()) {
      keycloakService.removeAuthResources(moduleDescriptor, realm);
    }
  }
}
