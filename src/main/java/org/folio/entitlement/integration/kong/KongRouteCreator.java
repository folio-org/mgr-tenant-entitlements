package org.folio.entitlement.integration.kong;

import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.service.stage.StageContextUtils.getApplicationDescriptor;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.Cancellable;
import org.folio.flow.api.StageContext;

@RequiredArgsConstructor
public class KongRouteCreator extends DatabaseLoggingStage implements Cancellable {

  private final KongGatewayService kongGatewayService;

  @Override
  public void execute(StageContext context) {
    var tenantName = context.<String>get(PARAM_TENANT_NAME);
    var applicationDescriptor = getApplicationDescriptor(context);
    kongGatewayService.addRoutes(tenantName, applicationDescriptor);
  }

  @Override
  public void cancel(StageContext context) {
    var tenantName = context.<String>get(PARAM_TENANT_NAME);
    var applicationDescriptor = getApplicationDescriptor(context);
    kongGatewayService.removeRoutes(tenantName, applicationDescriptor);
  }

  @Override
  public boolean shouldCancelIfFailed(StageContext context) {
    return true;
  }
}
