package org.folio.entitlement.integration.okapi.flow;

import static java.util.Arrays.asList;
import static org.folio.entitlement.utils.FlowUtils.combineStages;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceUpdater;
import org.folio.entitlement.integration.kong.KongRouteUpdater;
import org.folio.entitlement.integration.okapi.stage.OkapiModulesEventPublisher;
import org.folio.entitlement.integration.okapi.stage.OkapiModulesInstaller;
import org.folio.flow.api.Flow;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor
public class OkapiModulesUpgradeFlowFactory implements OkapiModulesFlowFactory {

  private final OkapiModulesInstaller moduleInstaller;
  private final OkapiModulesEventPublisher okapiModulesEventPublisher;

  @Setter(onMethod_ = @__(@Autowired(required = false)))
  private KongRouteUpdater kongRouteCreator;

  @Setter(onMethod_ = @__(@Autowired(required = false)))
  private KeycloakAuthResourceUpdater keycloakAuthResourceCreator;

  @Override
  public Flow createFlow(ApplicationStageContext context) {
    var request = context.getEntitlementRequest();
    return Flow.builder()
      .id(context.flowId() + "/OkapiModulesUpgradeFlow")
      .stage(combineStages("ParallelResourcesUpdater", asList(kongRouteCreator, keycloakAuthResourceCreator)))
      .stage(moduleInstaller)
      .stage(okapiModulesEventPublisher)
      .executionStrategy(request.getExecutionStrategy())
      .build();
  }

  @Override
  public EntitlementType getEntitlementType() {
    return EntitlementType.UPGRADE;
  }
}
