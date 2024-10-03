package org.folio.entitlement.integration.keycloak;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.okapi.model.OkapiStageContext;
import org.folio.entitlement.retry.annotations.KeycloakCallsRetryable;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.keycloak.admin.client.Keycloak;

@RequiredArgsConstructor
@KeycloakCallsRetryable
public class KeycloakAuthResourceUpdater extends DatabaseLoggingStage<OkapiStageContext> {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;

  @Override
  public void execute(OkapiStageContext context) {
    var tenantName = context.getTenantName();
    var moduleDescriptorHolders = context.getModuleDescriptorHolders();

    keycloakClient.tokenManager().grantToken();
    for (var moduleDescriptorHolder : moduleDescriptorHolders) {
      var moduleDescriptor = moduleDescriptorHolder.moduleDescriptor();
      var installedModuleDescriptor = moduleDescriptorHolder.installedModuleDescriptor();
      if (moduleDescriptorHolder.isVersionChanged()) {
        keycloakService.updateAuthResources(installedModuleDescriptor, moduleDescriptor, tenantName);
      }
    }

    var deprecatedModuleDescriptors = context.getDeprecatedModuleDescriptors();
    for (var deprecatedModuleDescriptor : deprecatedModuleDescriptors) {
      keycloakService.updateAuthResources(deprecatedModuleDescriptor, null, tenantName);
    }
  }
}
