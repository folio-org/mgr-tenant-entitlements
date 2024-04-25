package org.folio.entitlement.integration.kong;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.tools.kong.service.KongGatewayService;

@Log4j2
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
    var request = context.getEntitlementRequest();
    if (!request.isPurgeOnRollback()) {
      log.debug("Skipping purge of Kong routes during rollback: applicationId = {}",  context.getApplicationId());
      return;
    }

    var tenantName = context.getTenantName();
    var applicationDescriptor = context.getApplicationDescriptor();
    kongGatewayService.removeRoutes(tenantName, applicationDescriptor.getModuleDescriptors());
  }

  @Override
  public boolean shouldCancelIfFailed(ApplicationStageContext context) {
    return true;
  }
}
