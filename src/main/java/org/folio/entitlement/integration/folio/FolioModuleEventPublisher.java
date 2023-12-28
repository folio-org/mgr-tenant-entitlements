package org.folio.entitlement.integration.folio;

import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.service.stage.StageContextUtils.getEntitlementRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.integration.kafka.EntitlementEventPublisher;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.Cancellable;
import org.folio.flow.api.StageContext;

@Log4j2
@RequiredArgsConstructor
public class FolioModuleEventPublisher extends DatabaseLoggingStage implements Cancellable {

  private final EntitlementEventPublisher publisher;

  @Override
  public void execute(StageContext context) {
    var request = getEntitlementRequest(context);
    var event = prepareEntitlementEvent(context, request.getType());
    publisher.publish(event);
    log.debug("Published event: event = {}", event);
  }

  @Override
  public void cancel(StageContext context) {
    var request = getEntitlementRequest(context);
    if (request.getType() == EntitlementType.REVOKE) {
      return;
    }

    var event = prepareEntitlementEvent(context, EntitlementType.REVOKE);
    publisher.publish(event);
    log.debug("Published event: event = {}", event);
  }

  @Override
  public String getStageName(StageContext context) {
    return context.<String>getFlowParameter(PARAM_MODULE_ID) + "-moduleEventPublisher";
  }

  private static EntitlementEvent prepareEntitlementEvent(StageContext context, EntitlementType type) {
    var moduleId = context.<String>getFlowParameter(PARAM_MODULE_ID);
    var request = getEntitlementRequest(context);
    var tenantName = context.<String>get(PARAM_TENANT_NAME);
    return new EntitlementEvent(type.name(), moduleId, tenantName, request.getTenantId());
  }
}
