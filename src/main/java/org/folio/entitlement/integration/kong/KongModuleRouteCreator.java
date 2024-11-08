package org.folio.entitlement.integration.kong;

import static java.util.Collections.singletonList;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.folio.tools.kong.service.KongGatewayService;

@Log4j2
@RequiredArgsConstructor
public class KongModuleRouteCreator extends ModuleDatabaseLoggingStage {

  private final KongGatewayService kongGatewayService;
  private final ThreadLocalModuleStageContext threadLocalModuleStageContext;

  @Override
  public void execute(ModuleStageContext context) {
    threadLocalModuleStageContext.set(context, getStageName(context));

    var tenantName = context.getTenantName();
    kongGatewayService.addRoutes(tenantName, singletonList(context.getModuleDescriptor()));
    threadLocalModuleStageContext.clear();
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
}
