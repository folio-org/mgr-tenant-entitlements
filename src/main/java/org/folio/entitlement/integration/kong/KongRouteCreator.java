package org.folio.entitlement.integration.kong;

import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.service.stage.StageContextUtils.getApplicationDescriptor;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.Cancellable;
import org.folio.flow.api.StageContext;
import org.folio.tools.kong.service.KongGatewayService;

@RequiredArgsConstructor
public class KongRouteCreator extends DatabaseLoggingStage implements Cancellable {

  private final KongGatewayService kongGatewayService;

  @Override
  public void execute(StageContext context) {
    var tenantName = context.<String>get(PARAM_TENANT_NAME);
    var applicationDescriptor = getApplicationDescriptor(context);
    kongGatewayService.addRoutes(tenantName, emptyIfNull(applicationDescriptor.getModuleDescriptors()));
  }

  @Override
  public void cancel(StageContext context) {
    var tenantName = context.<String>get(PARAM_TENANT_NAME);
    var applicationDescriptor = getApplicationDescriptor(context);
    kongGatewayService.removeRoutes(tenantName, emptyIfNull(applicationDescriptor.getModuleDescriptors()));
  }

  @Override
  public boolean shouldCancelIfFailed(StageContext context) {
    return true;
  }
}
