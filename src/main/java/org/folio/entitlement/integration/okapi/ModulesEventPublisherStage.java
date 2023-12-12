package org.folio.entitlement.integration.okapi;

import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.kafka.EntitlementEventPublisher;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.StageContext;

@RequiredArgsConstructor
public class ModulesEventPublisherStage extends DatabaseLoggingStage {

  private final EntitlementEventPublisher entitlementEventPublisher;

  @Override
  public void execute(StageContext context) {
    var applicationDescriptor = context.<ApplicationDescriptor>get(PARAM_APP_DESCRIPTOR);
    for (var module : applicationDescriptor.getModules()) {
      var request = context.<EntitlementRequest>getFlowParameter(PARAM_REQUEST);
      var tenantName = context.<String>get(PARAM_TENANT_NAME);
      var event = new EntitlementEvent(request.getType().name(), module.getId(), tenantName, request.getTenantId());
      entitlementEventPublisher.publish(event);
    }
  }
}
