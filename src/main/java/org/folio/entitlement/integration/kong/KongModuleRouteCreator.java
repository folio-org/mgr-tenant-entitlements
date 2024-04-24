package org.folio.entitlement.integration.kong;

import static java.util.Collections.singletonList;
import static org.apache.commons.lang3.StringUtils.uncapitalize;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.tools.kong.service.KongGatewayService;

@Log4j2
@RequiredArgsConstructor
public class KongModuleRouteCreator extends DatabaseLoggingStage<ModuleStageContext> {

  private final KongGatewayService kongGatewayService;

  @Override
  public void execute(ModuleStageContext context) {
    var tenantName = context.getTenantName();
    kongGatewayService.addRoutes(tenantName, singletonList(context.getModuleDescriptor()));
  }

  @Override
  public void cancel(ModuleStageContext context) {
    var request = context.getEntitlementRequest();
    if (!request.isPurgeOnRollback()) {
      log.debug("Skipping purge of Kong routes during rollback: moduleId = {}",  context.getModuleId());
      return;
    }

    var tenantName = context.getTenantName();
    kongGatewayService.removeRoutes(tenantName, singletonList(context.getModuleDescriptor()));
  }

  @Override
  public boolean shouldCancelIfFailed(ModuleStageContext context) {
    return true;
  }

  @Override
  public String getStageName(ModuleStageContext context) {
    return context.getModuleId() + "-" + uncapitalize(this.getClass().getSimpleName());
  }
}
