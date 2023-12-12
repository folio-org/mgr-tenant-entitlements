package org.folio.entitlement.integration.kong;

import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.Cancellable;
import org.folio.flow.api.StageContext;

@RequiredArgsConstructor
public class KongRouteCreator extends DatabaseLoggingStage implements Cancellable {

  private final KongGatewayService kongGatewayService;

  @Override
  public void execute(StageContext context) {
    var tenantName = context.<String>get(PARAM_TENANT_NAME);
    var applicationDescriptor = context.<ApplicationDescriptor>get(PARAM_APP_DESCRIPTOR);
    kongGatewayService.addRoutes(tenantName, applicationDescriptor);
  }

  @Override
  public void cancel(StageContext context) {
    var tenantName = context.<String>get(PARAM_TENANT_NAME);
    var applicationDescriptor = context.<ApplicationDescriptor>get(PARAM_APP_DESCRIPTOR);
    kongGatewayService.removeRoutes(tenantName, applicationDescriptor);
  }

  @Override
  public boolean shouldCancelIfFailed(StageContext context) {
    return true;
  }
}
