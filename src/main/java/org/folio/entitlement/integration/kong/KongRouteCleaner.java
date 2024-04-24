package org.folio.entitlement.integration.kong;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.tools.kong.service.KongGatewayService;

@RequiredArgsConstructor
public class KongRouteCleaner extends DatabaseLoggingStage<ApplicationStageContext> {

  private final KongGatewayService kongGatewayService;

  @Override
  public void execute(ApplicationStageContext context) {
    var request = context.getEntitlementRequest();
    if (!request.isPurge()) {
      return;
    }

    var applicationDescriptor = context.getApplicationDescriptor();
    kongGatewayService.removeRoutes(context.getTenantName(), applicationDescriptor.getModuleDescriptors());
  }
}
