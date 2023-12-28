package org.folio.entitlement.service.stage;

import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.service.stage.StageContextUtils.getEntitlementRequest;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.tm.TenantManagerService;
import org.folio.flow.api.StageContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantLoader extends DatabaseLoggingStage {

  private final TenantManagerService tenantManagerService;

  @Override
  public void execute(StageContext context) {
    var request = getEntitlementRequest(context);
    var tenant = tenantManagerService.findTenant(request.getTenantId(), request.getOkapiToken());
    context.put(PARAM_TENANT_NAME, tenant.getName());
  }
}
