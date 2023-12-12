package org.folio.entitlement.integration.okapi;

import lombok.RequiredArgsConstructor;
import org.folio.flow.api.Flow;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;

@RequiredArgsConstructor
public class OkapiModuleInstallerFlowProvider {

  private final OkapiModulesInstaller moduleInstaller;
  private final ModulesEventPublisherStage modulesEventPublisherStage;

  public Stage prepareFlow(StageContext context) {
    var flowId = context.flowId() + "/okapi-module-installer";
    return Flow.builder()
      .id(flowId)
      .stage(moduleInstaller)
      .stage(modulesEventPublisherStage)
      .build();
  }
}
