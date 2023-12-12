package org.folio.entitlement.integration.folio;

import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_ID;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.StageContext;

@Log4j2
@RequiredArgsConstructor
public class FolioModuleUninstaller extends DatabaseLoggingStage {

  private final FolioModuleService folioModuleService;

  @Override
  public void execute(StageContext context) {
    var moduleRequest = ModuleRequest.fromStageContext(context);
    folioModuleService.disable(moduleRequest);
  }

  @Override
  public String getStageName(StageContext context) {
    return context.<String>getFlowParameter(PARAM_MODULE_ID) + "-moduleUninstaller";
  }
}
