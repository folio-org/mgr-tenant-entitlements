package org.folio.entitlement.integration.kong;

import static org.folio.entitlement.utils.EntitlementServiceUtils.isModuleUpdated;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;
import org.folio.tools.kong.service.KongGatewayService;

@RequiredArgsConstructor
public class KongModuleRouteUpdater extends ModuleDatabaseLoggingStage {

  private final KongGatewayService kongGatewayService;

  @Override
  public void execute(ModuleStageContext context) {
    var tenantName = context.getTenantName();
    var moduleDescriptor = context.getModuleDescriptor();
    var installedModuleDescriptor = context.getInstalledModuleDescriptor();
    if (!isModuleUpdated(moduleDescriptor, installedModuleDescriptor)) {
      return;
    }

    if (installedModuleDescriptor != null) {
      kongGatewayService.removeRoutes(tenantName, List.of(installedModuleDescriptor));
    }

    if (moduleDescriptor != null) {
      kongGatewayService.addRoutes(tenantName, List.of(moduleDescriptor));
    }
  }
}
