package org.folio.entitlement.integration.kong;

import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.StageContext;

@RequiredArgsConstructor
public class KongRouteCleaner extends DatabaseLoggingStage {

  private final KongGatewayService kongGatewayService;

  @Override
  public void execute(StageContext context) {
    var request = context.<EntitlementRequest>getFlowParameter(PARAM_REQUEST);
    if (!request.isPurge()) {
      return;
    }

    var tenantName = context.<String>get(PARAM_TENANT_NAME);
    var applicationDescriptor = context.<ApplicationDescriptor>get(PARAM_APP_DESCRIPTOR);
    kongGatewayService.removeRoutes(tenantName, applicationDescriptor);
  }
}
