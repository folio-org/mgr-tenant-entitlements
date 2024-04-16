package org.folio.entitlement.integration.keycloak;

import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.common.utils.Collectors.toLinkedHashMap;
import static org.folio.entitlement.utils.SemverUtils.getName;

import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.folio.ApplicationStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.keycloak.admin.client.Keycloak;

@RequiredArgsConstructor
public class KeycloakAuthResourceUpdater extends DatabaseLoggingStage<ApplicationStageContext> {

  private final Keycloak keycloakClient;
  private final KeycloakService keycloakService;

  @Override
  public void execute(ApplicationStageContext context) {
    var tenantName = context.getTenantName();
    var entitledApplicationDescriptor = context.getEntitledApplicationDescriptor();
    var applicationDescriptor = context.getApplicationDescriptor();

    var entitledModuleDescriptors = groupModulesByNames(entitledApplicationDescriptor.getModuleDescriptors());
    var moduleDescriptors = groupModulesByNames(applicationDescriptor.getModuleDescriptors());

    keycloakClient.tokenManager().grantToken();
    for (var moduleDescriptorEntry : moduleDescriptors.entrySet()) {
      var moduleName = moduleDescriptorEntry.getKey();
      var entitledModuleDescriptor = entitledModuleDescriptors.get(moduleName);
      keycloakService.updateAuthResources(entitledModuleDescriptor, moduleDescriptorEntry.getValue(), tenantName);
    }

    for (var moduleDescriptorEntry : entitledModuleDescriptors.entrySet()) {
      var moduleName = moduleDescriptorEntry.getKey();
      var moduleDescriptor = moduleDescriptors.get(moduleName);
      if (moduleDescriptor == null) {
        keycloakService.updateAuthResources(moduleDescriptorEntry.getValue(), null, tenantName);
      }
    }
  }

  private static Map<String, ModuleDescriptor> groupModulesByNames(List<ModuleDescriptor> moduleDescriptors) {
    return toStream(moduleDescriptors).collect(toLinkedHashMap(desc -> getName(desc.getId())));
  }
}
