package org.folio.entitlement.integration.kong;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.service.stage.StageContextUtils.getApplicationDescriptor;
import static org.folio.entitlement.service.stage.StageContextUtils.getEntitlementRequest;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.StageContext;
import org.folio.tools.kong.service.KongGatewayService;

@RequiredArgsConstructor
public class KongRouteCleaner extends DatabaseLoggingStage {

  private final KongGatewayService kongGatewayService;

  @Override
  public void execute(StageContext context) {
    var request = getEntitlementRequest(context);
    if (!request.isPurge()) {
      return;
    }

    var tenantName = context.<String>get(PARAM_TENANT_NAME);
    var applicationDescriptor = getApplicationDescriptor(context);
    kongGatewayService.removeRoutes(tenantName, emptyIfNull(applicationDescriptor.getModuleDescriptors()));
  }
}
