package org.folio.entitlement.integration.keycloak;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.service.stage.StageContextUtils.getApplicationDescriptor;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.Cancellable;
import org.folio.flow.api.StageContext;
import org.keycloak.admin.client.Keycloak;

@RequiredArgsConstructor
public class KeycloakAuthResourceCreator extends DatabaseLoggingStage implements Cancellable {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;

  @Override
  public void execute(StageContext context) {
    var realm = context.<String>get(PARAM_TENANT_NAME);
    var applicationDescriptor = getApplicationDescriptor(context);
    var moduleDescriptors = applicationDescriptor.getModuleDescriptors();
    keycloakClient.tokenManager().grantToken();
    emptyIfNull(moduleDescriptors).forEach(descriptor -> keycloakService.registerModuleResources(descriptor, realm));
  }

  @Override
  public void cancel(StageContext context) {
    var realm = context.<String>get(PARAM_TENANT_NAME);
    var applicationDescriptor = getApplicationDescriptor(context);
    var moduleDescriptors = applicationDescriptor.getModuleDescriptors();
    keycloakClient.tokenManager().grantToken();
    emptyIfNull(moduleDescriptors).forEach(descriptor -> keycloakService.unregisterModuleResources(descriptor, realm));
  }

  @Override
  public boolean shouldCancelIfFailed(StageContext context) {
    return true;
  }
}
