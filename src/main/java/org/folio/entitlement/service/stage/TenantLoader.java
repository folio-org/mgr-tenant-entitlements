package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.folio.CommonStageContext;
import org.folio.entitlement.integration.tm.TenantManagerService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantLoader extends DatabaseLoggingStage<CommonStageContext> {

  private final TenantManagerService tenantManagerService;

  @Override
  public void execute(CommonStageContext context) {
    var request = context.getEntitlementRequest();
    var tenant = tenantManagerService.findTenant(request.getTenantId(), request.getAuthToken());
    context.withTenantName(tenant.getName());
  }
}
