package org.folio.entitlement.integration.kong;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.okapi.model.OkapiStageContext;
import org.folio.entitlement.retry.KongCallsRetryable;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.tools.kong.service.KongGatewayService;

@RequiredArgsConstructor
@KongCallsRetryable
public class KongRouteCleaner extends DatabaseLoggingStage<OkapiStageContext> {

  private final KongGatewayService kongGatewayService;

  @Override
  public void execute(OkapiStageContext context) {
    var request = context.getEntitlementRequest();
    if (!request.isPurge()) {
      return;
    }

    kongGatewayService.removeRoutes(context.getTenantName(), context.getModuleDescriptors());
  }
}
