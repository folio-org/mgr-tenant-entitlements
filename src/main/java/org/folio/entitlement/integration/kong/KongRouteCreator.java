package org.folio.entitlement.integration.kong;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.okapi.model.OkapiStageContext;
import org.folio.entitlement.retry.KongCallsRetryable;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.tools.kong.service.KongGatewayService;

@Log4j2
@RequiredArgsConstructor
@KongCallsRetryable
public class KongRouteCreator extends DatabaseLoggingStage<OkapiStageContext> {

  private final KongGatewayService kongGatewayService;

  @Override
  public void execute(OkapiStageContext context) {
    var tenantName = context.getTenantName();
    var moduleDescriptors = context.getModuleDescriptors();
    kongGatewayService.addRoutes(tenantName, moduleDescriptors);
  }

  @Override
  public void cancel(OkapiStageContext context) {
    var request = context.getEntitlementRequest();
    if (!request.isPurgeOnRollback()) {
      log.debug("Skipping purge of Kong routes during rollback: applicationId = {}",  context.getApplicationId());
      return;
    }

    kongGatewayService.removeRoutes(context.getTenantName(), context.getModuleDescriptors());
  }

  @Override
  public boolean shouldCancelIfFailed(OkapiStageContext context) {
    return true;
  }
}
