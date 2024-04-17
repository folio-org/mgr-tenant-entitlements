package org.folio.entitlement.integration.okapi;

import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.folio.entitlement.utils.FlowUtils.getEntitlementRequest;

import lombok.RequiredArgsConstructor;
import org.folio.flow.api.Flow;
import org.folio.flow.api.StageContext;

@RequiredArgsConstructor
public class OkapiModuleInstallerFlowProvider {

  private final OkapiModulesInstaller moduleInstaller;
  private final ModulesEventPublisherStage modulesEventPublisherStage;

  public Flow prepareFlow(StageContext context) {
    var request = getEntitlementRequest(context);
    return Flow.builder()
      .id(context.flowId() + "/OkapiModule" + capitalize(request.getType().getValue()) + "Flow")
      .stage(moduleInstaller)
      .stage(modulesEventPublisherStage)
      .executionStrategy(request.getExecutionStrategy())
      .build();
  }
}
