package org.folio.entitlement.integration.okapi.stage;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.kafka.EntitlementEventPublisher;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.integration.okapi.model.OkapiStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;

@RequiredArgsConstructor
public class OkapiModulesEventPublisher extends DatabaseLoggingStage<OkapiStageContext> {

  private final EntitlementEventPublisher entitlementEventPublisher;

  @Override
  public void execute(OkapiStageContext context) {
    var request = context.getEntitlementRequest();
    var tenantName = context.getTenantName();
    var tenantId = context.getTenantId();
    var requestType = request.getType().name();

    for (var module : context.getModuleDescriptors()) {
      var event = new EntitlementEvent(requestType, module.getId(), tenantName, tenantId);
      entitlementEventPublisher.publish(event);
    }
  }
}
