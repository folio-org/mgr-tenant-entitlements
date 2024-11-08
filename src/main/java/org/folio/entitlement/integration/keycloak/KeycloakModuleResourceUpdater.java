package org.folio.entitlement.integration.keycloak;

import static org.folio.entitlement.utils.EntitlementServiceUtils.isModuleUpdated;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.retry.annotations.KeycloakCallsRetryable;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.keycloak.admin.client.Keycloak;

@RequiredArgsConstructor
@KeycloakCallsRetryable
public class KeycloakModuleResourceUpdater extends ModuleDatabaseLoggingStage {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;
  private final ThreadLocalModuleStageContext threadLocalModuleStageContext;

  @Override
  public void execute(ModuleStageContext context) {
    threadLocalModuleStageContext.set(context, getStageName(context));

    var tenantName = context.getTenantName();
    var moduleDescriptor = context.getModuleDescriptor();
    var entitledModuleDescriptor = context.getInstalledModuleDescriptor();

    if (isModuleUpdated(moduleDescriptor, entitledModuleDescriptor)) {
      keycloakClient.tokenManager().grantToken();
      keycloakService.updateAuthResources(entitledModuleDescriptor, moduleDescriptor, tenantName);
    }
  }
}
