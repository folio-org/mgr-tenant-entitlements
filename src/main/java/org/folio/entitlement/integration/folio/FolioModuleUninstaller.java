package org.folio.entitlement.integration.folio;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;

@Log4j2
@RequiredArgsConstructor
public class FolioModuleUninstaller extends DatabaseLoggingStage<ApplicationStageContext> {

  private final FolioModuleService folioModuleService;

  @Override
  public void execute(ApplicationStageContext context) {
    var moduleRequest = ModuleRequest.fromStageContext(context);
    folioModuleService.disable(moduleRequest);
  }

  @Override
  public String getStageName(ApplicationStageContext context) {
    return context.getModuleId() + "-moduleUninstaller";
  }
}
