package org.folio.entitlement.integration.kong;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.folio.common.utils.CollectionUtils.toStream;

import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.tools.kong.service.KongGatewayService;

@RequiredArgsConstructor
public class KongRouteUpdater extends DatabaseLoggingStage<ApplicationStageContext> {

  private final KongGatewayService kongGatewayService;

  @Override
  public void execute(ApplicationStageContext context) {
    var tenantName = context.getTenantName();
    var applicationDescriptor = context.getApplicationDescriptor();
    kongGatewayService.updateRoutes(tenantName, applicationDescriptor.getModuleDescriptors());

    var moduleDescriptors = toStream(applicationDescriptor.getModuleDescriptors())
      .map(ModuleDescriptor::getId)
      .collect(toUnmodifiableSet());

    var entitledAppDesc = context.getEntitledApplicationDescriptor();
    var deprecatedModuleDescriptors = toStream(entitledAppDesc.getModuleDescriptors())
      .filter(moduleDescriptor -> !moduleDescriptors.contains(moduleDescriptor.getId()))
      .toList();

    kongGatewayService.removeRoutes(tenantName, deprecatedModuleDescriptors);
  }
}
