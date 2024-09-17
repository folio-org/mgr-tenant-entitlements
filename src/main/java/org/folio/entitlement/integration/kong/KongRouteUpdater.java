package org.folio.entitlement.integration.kong;

import static java.util.Collections.singletonList;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ModuleDescriptorHolder;
import org.folio.entitlement.integration.okapi.model.OkapiStageContext;
import org.folio.entitlement.retry.KongCallsRetryable;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.tools.kong.service.KongGatewayService;

@RequiredArgsConstructor
@KongCallsRetryable
public class KongRouteUpdater extends DatabaseLoggingStage<OkapiStageContext> {

  private final KongGatewayService kongGatewayService;

  @Override
  public void execute(OkapiStageContext context) {
    var tenantName = context.getTenantName();
    for (var moduleDescriptorHolder : context.getModuleDescriptorHolders()) {
      if (moduleDescriptorHolder.isVersionChanged()) {
        upgradeModuleRoutes(moduleDescriptorHolder, tenantName);
      }
    }

    kongGatewayService.removeRoutes(tenantName, context.getDeprecatedModuleDescriptors());
  }

  private void upgradeModuleRoutes(ModuleDescriptorHolder moduleDescriptorHolder, String tenantName) {
    var moduleDescriptor = moduleDescriptorHolder.moduleDescriptor();
    kongGatewayService.updateRoutes(tenantName, singletonList(moduleDescriptor));

    var installedModuleDescriptor = moduleDescriptorHolder.installedModuleDescriptor();
    if (installedModuleDescriptor != null) {
      kongGatewayService.removeRoutes(tenantName, singletonList(installedModuleDescriptor));
    }
  }
}
