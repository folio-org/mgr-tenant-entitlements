package org.folio.entitlement.integration.keycloak;

import static java.util.Optional.ofNullable;
import static org.folio.entitlement.utils.EntitlementServiceUtils.groupModulesByNames;
import static org.folio.entitlement.utils.EntitlementServiceUtils.toHashMap;

import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.keycloak.admin.client.Keycloak;

@RequiredArgsConstructor
public class KeycloakAuthResourceUpdater extends DatabaseLoggingStage<ApplicationStageContext> {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;

  @Override
  public void execute(ApplicationStageContext context) {
    var tenantName = context.getTenantName();
    var entitledAppDescriptor = context.getEntitledApplicationDescriptor();
    var appDescriptor = context.getApplicationDescriptor();

    var entitledModulesByName = groupModulesByNames(entitledAppDescriptor.getModules());
    var modulesByName = groupModulesByNames(appDescriptor.getModules());
    var descriptorsById = toHashMap(appDescriptor.getModuleDescriptors(), ModuleDescriptor::getId);
    var entitledDescriptorsById = toHashMap(entitledAppDescriptor.getModuleDescriptors(), ModuleDescriptor::getId);

    keycloakClient.tokenManager().grantToken();
    for (var moduleEntry : modulesByName.entrySet()) {
      var moduleName = moduleEntry.getKey();
      var module = moduleEntry.getValue();
      var entitledModuleDescriptor = ofNullable(entitledModulesByName.get(moduleName))
        .map(entitledModule -> entitledDescriptorsById.get(entitledModule.getId()))
        .orElse(null);

      keycloakService.updateAuthResources(entitledModuleDescriptor, descriptorsById.get(module.getId()), tenantName);
    }

    for (var entitledModuleEntry : entitledModulesByName.entrySet()) {
      var moduleName = entitledModuleEntry.getKey();
      var module = modulesByName.get(moduleName);
      if (module == null) {
        var entitledModuleDescriptor = entitledDescriptorsById.get(entitledModuleEntry.getValue().getId());
        keycloakService.updateAuthResources(entitledModuleDescriptor, null, tenantName);
      }
    }
  }
}
