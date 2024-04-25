package org.folio.entitlement.integration.folio.stage;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.folio.FolioModuleService;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;

@Log4j2
@RequiredArgsConstructor
public class FolioModuleInstaller extends ModuleDatabaseLoggingStage {

  private final FolioModuleService folioModuleService;

  @Override
  public void execute(ModuleStageContext context) {
    var moduleRequest = ModuleRequest.fromStageContext(context);
    folioModuleService.enable(moduleRequest);
  }

  @Override
  public void cancel(ModuleStageContext context) {
    var request = context.getEntitlementRequest();
    var moduleRequest = ModuleRequest.fromStageContext(context, request.isPurgeOnRollback());
    folioModuleService.disable(moduleRequest);
  }
}
