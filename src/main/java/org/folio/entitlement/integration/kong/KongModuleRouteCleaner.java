package org.folio.entitlement.integration.kong;

import static org.apache.commons.lang3.StringUtils.uncapitalize;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.tools.kong.service.KongGatewayService;

@RequiredArgsConstructor
public class KongModuleRouteCleaner extends DatabaseLoggingStage<ModuleStageContext> {

  private final KongGatewayService kongGatewayService;

  @Override
  public void execute(ModuleStageContext context) {
    var request = context.getEntitlementRequest();
    if (!request.isPurge()) {
      return;
    }

    var moduleDescriptor = context.getModuleDescriptor();
    kongGatewayService.removeRoutes(context.getTenantName(), List.of(moduleDescriptor));
  }

  @Override
  public String getStageName(ModuleStageContext context) {
    return context.getModuleId() + "-" + uncapitalize(this.getClass().getSimpleName());
  }
}
