package org.folio.entitlement.integration.kong;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.folio.ApplicationStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.tools.kong.service.KongGatewayService;

@RequiredArgsConstructor
public class KongRouteCreator extends DatabaseLoggingStage<ApplicationStageContext> {

  private final KongGatewayService kongGatewayService;

  @Override
  public void execute(ApplicationStageContext context) {
    var tenantName = context.getTenantName();
    var applicationDescriptor = context.getApplicationDescriptor();
    kongGatewayService.addRoutes(tenantName, applicationDescriptor.getModuleDescriptors());
  }

  @Override
  public void cancel(ApplicationStageContext context) {
    var tenantName = context.getTenantName();
    var applicationDescriptor = context.getApplicationDescriptor();
    kongGatewayService.removeRoutes(tenantName, applicationDescriptor.getModuleDescriptors());
  }

  @Override
  public boolean shouldCancelIfFailed(ApplicationStageContext context) {
    return true;
  }
}
