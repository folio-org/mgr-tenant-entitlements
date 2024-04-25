package org.folio.entitlement.integration.keycloak;

import static org.apache.commons.lang3.StringUtils.uncapitalize;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.keycloak.admin.client.Keycloak;

@Log4j2
@RequiredArgsConstructor
public class KeycloakModuleResourceCleaner extends DatabaseLoggingStage<ModuleStageContext> {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;

  @Override
  public void execute(ModuleStageContext context) {
    var request = context.getEntitlementRequest();
    var moduleDescriptor = context.getModuleDescriptor();
    if (!request.isPurge()) {
      log.info("Skipping purge of keycloak module resources: moduleId = {}", moduleDescriptor.getId());
      return;
    }

    var tenantName = context.getTenantName();
    keycloakClient.tokenManager().grantToken();
    keycloakService.removeAuthResources(moduleDescriptor, tenantName);
  }

  @Override
  public String getStageName(ModuleStageContext context) {
    return context.getModuleId() + "-" + uncapitalize(this.getClass().getSimpleName());
  }
}
