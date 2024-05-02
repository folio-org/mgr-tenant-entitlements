package org.folio.entitlement.integration.okapi.flow;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.folio.entitlement.utils.FlowUtils.combineStages;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.integration.kafka.ScheduledJobEventPublisher;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceUpdater;
import org.folio.entitlement.integration.kong.KongRouteUpdater;
import org.folio.flow.api.Flow;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor
public class OkapiModulesUpgradeFlowFactory implements OkapiModulesFlowFactory {

  private final ScheduledJobEventPublisher scheduledJobEventPublisher;

  private KongRouteUpdater kongRouteUpdater;
  private KeycloakAuthResourceUpdater keycloakAuthResourceUpdater;

  @Override
  public Flow createFlow(ApplicationStageContext context, Map<?, ?> additionalFlowParameters) {
    var request = context.getEntitlementRequest();
    return Flow.builder()
      .id(context.flowId() + "/OkapiModulesUpgradeFlow")
      .stage(combineStages("ParallelResourcesUpdater", asList(kongRouteUpdater, keycloakAuthResourceUpdater)))
      .stage(combineStages("EventPublishingParallelStage", singletonList(scheduledJobEventPublisher)))
      .executionStrategy(request.getExecutionStrategy())
      .flowParameters(additionalFlowParameters)
      .build();
  }

  @Override
  public EntitlementType getEntitlementType() {
    return EntitlementType.UPGRADE;
  }

  @Autowired(required = false)
  public void setKongRouteUpdater(KongRouteUpdater kongRouteCreator) {
    this.kongRouteUpdater = kongRouteCreator;
  }

  @Autowired(required = false)
  public void setKeycloakAuthResourceUpdater(KeycloakAuthResourceUpdater keycloakAuthResourceUpdater) {
    this.keycloakAuthResourceUpdater = keycloakAuthResourceUpdater;
  }
}
