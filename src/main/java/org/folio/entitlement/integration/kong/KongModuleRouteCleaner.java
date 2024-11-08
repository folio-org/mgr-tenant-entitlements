package org.folio.entitlement.integration.kong;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.folio.tools.kong.service.KongGatewayService;

@RequiredArgsConstructor
public class KongModuleRouteCleaner extends ModuleDatabaseLoggingStage {

  private final KongGatewayService kongGatewayService;
  private final ThreadLocalModuleStageContext threadLocalModuleStageContext;

  @Override
  public void execute(ModuleStageContext context) {
    threadLocalModuleStageContext.set(context, getStageName(context));

    var moduleDescriptor = context.getModuleDescriptor();
    kongGatewayService.removeRoutes(context.getTenantName(), List.of(moduleDescriptor));
  }
}
