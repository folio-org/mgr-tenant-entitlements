package org.folio.entitlement.integration.okapi.flow;

import static java.util.Arrays.asList;
import static org.folio.entitlement.utils.FlowUtils.combineStages;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.integration.kafka.CapabilitiesEventPublisher;
import org.folio.entitlement.integration.kafka.ScheduledJobEventPublisher;
import org.folio.entitlement.integration.kafka.SystemUserEventPublisher;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCreator;
import org.folio.entitlement.integration.kong.KongRouteCreator;
import org.folio.entitlement.integration.okapi.stage.OkapiModulesEventPublisher;
import org.folio.entitlement.integration.okapi.stage.OkapiModulesInstaller;
import org.folio.flow.api.Flow;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor
public class OkapiModulesEntitleFlowFactory implements OkapiModulesFlowFactory {

  private final OkapiModulesInstaller moduleInstaller;

  private final SystemUserEventPublisher sysUserEventPublisher;
  private final ScheduledJobEventPublisher scheduledJobEventPublisher;
  private final CapabilitiesEventPublisher capabilitiesEventPublisher;
  private final OkapiModulesEventPublisher okapiModulesEventPublisher;

  @Setter(onMethod_ = @__(@Autowired(required = false)))
  private KongRouteCreator kongRouteCreator;

  @Setter(onMethod_ = @__(@Autowired(required = false)))
  private KeycloakAuthResourceCreator kcAuthResourceCreator;

  @Override
  public Flow createFlow(ApplicationStageContext context) {
    var request = context.getEntitlementRequest();
    return Flow.builder()
      .id(context.flowId() + "/OkapiModulesEntitleFlow")
      .stage(combineStages("ParallelResourcesCleaner", asList(kongRouteCreator, kcAuthResourceCreator)))
      .stage(moduleInstaller)
      .stage(combineStages("EventPublishingParallelStage", asList(
        sysUserEventPublisher, scheduledJobEventPublisher, capabilitiesEventPublisher)))
      .stage(okapiModulesEventPublisher)
      .executionStrategy(request.getExecutionStrategy())
      .build();
  }

  @Override
  public EntitlementType getEntitlementType() {
    return EntitlementType.ENTITLE;
  }
}
