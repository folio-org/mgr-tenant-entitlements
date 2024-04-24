package org.folio.entitlement.integration.okapi.stage;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.integration.kafka.EntitlementEventPublisher;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;

@RequiredArgsConstructor
public class OkapiModulesEventPublisher extends DatabaseLoggingStage<ApplicationStageContext> {

  private final EntitlementEventPublisher entitlementEventPublisher;

  @Override
  public void execute(ApplicationStageContext context) {
    var applicationDescriptor = context.getApplicationDescriptor();
    for (var module : applicationDescriptor.getModules()) {
      var request = context.getEntitlementRequest();
      var tenantName = context.getTenantName();
      var event = new EntitlementEvent(request.getType().name(), module.getId(), tenantName, context.getTenantId());
      entitlementEventPublisher.publish(event);
    }
  }
}
