package org.folio.entitlement.integration.folio.stage;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.folio.FolioModuleService;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;

@Log4j2
@RequiredArgsConstructor
public class FolioModuleUninstaller extends DatabaseLoggingStage<ModuleStageContext> {

  private final FolioModuleService folioModuleService;

  @Override
  public void execute(ModuleStageContext context) {
    var moduleRequest = ModuleRequest.fromStageContext(context);
    folioModuleService.disable(moduleRequest);
  }

  @Override
  public String getStageName(ModuleStageContext context) {
    return context.getModuleId() + "-moduleUninstaller";
  }
}
