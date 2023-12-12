package org.folio.entitlement.integration.folio;

import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_ID;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.Cancellable;
import org.folio.flow.api.StageContext;

@Log4j2
@RequiredArgsConstructor
public class FolioModuleInstaller extends DatabaseLoggingStage implements Cancellable {

  private final FolioModuleService folioModuleService;

  @Override
  public void execute(StageContext context) {
    var moduleRequest = ModuleRequest.fromStageContext(context);
    folioModuleService.enable(moduleRequest);
  }

  @Override
  public void cancel(StageContext context) {
    var moduleRequest = ModuleRequest.fromStageContext(context, true);
    folioModuleService.disable(moduleRequest);
  }

  @Override
  public String getStageName(StageContext context) {
    return context.<String>getFlowParameter(PARAM_MODULE_ID) + "-moduleInstaller";
  }
}
