package org.folio.entitlement.integration.folio.stage;

import static java.util.Collections.singletonList;
import static org.folio.entitlement.utils.EntitlementServiceUtils.isModuleVersionChanged;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.folio.FolioModuleService;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;

@Log4j2
@RequiredArgsConstructor
public class FolioModuleUpdater extends ModuleDatabaseLoggingStage {

  private final FolioModuleService folioModuleService;
  private final EntitlementModuleService moduleService;

  @Override
  public void execute(ModuleStageContext context) {
    var moduleDescriptor = context.getModuleDescriptor();
    var installedModuleDescriptor = context.getInstalledModuleDescriptor();
    var tenantId = context.getTenantId();
    var entitledApplicationId = context.getEntitledApplicationId();

    if (!isModuleVersionChanged(moduleDescriptor, installedModuleDescriptor)) {
      var installedModuleDescriptorId = installedModuleDescriptor.getId();
      moduleService.deleteModuleEntitlement(installedModuleDescriptorId, tenantId, entitledApplicationId);
      moduleService.saveAll(tenantId, context.getApplicationId(), singletonList(moduleDescriptor.getId()));
      return;
    }

    if (moduleDescriptor != null) {
      var moduleRequest = ModuleRequest.fromStageContext(context);
      folioModuleService.enable(moduleRequest);
    }

    if (installedModuleDescriptor != null && moduleDescriptor == null) {
      var moduleId = installedModuleDescriptor.getId();
      moduleService.deleteModuleEntitlement(moduleId, tenantId, entitledApplicationId);
    }
  }
}
