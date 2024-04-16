package org.folio.entitlement.integration.folio;

import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.integration.kafka.EntitlementEventPublisher;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;

@Log4j2
@RequiredArgsConstructor
public class FolioModuleEventPublisher extends DatabaseLoggingStage<ApplicationStageContext> {

  private final EntitlementEventPublisher publisher;

  @Override
  public void execute(ApplicationStageContext context) {
    var request = context.getEntitlementRequest();
    var event = prepareEntitlementEvent(context, request.getType());
    publisher.publish(event);
    log.debug("Published event: event = {}", event);
  }

  @Override
  public void cancel(ApplicationStageContext context) {
    var request = context.getEntitlementRequest();
    if (request.getType() == REVOKE) {
      return;
    }

    var event = prepareEntitlementEvent(context, REVOKE);
    publisher.publish(event);
    log.debug("Published event: event = {}", event);
  }

  @Override
  public String getStageName(ApplicationStageContext context) {
    return context.getModuleId() + "-moduleEventPublisher";
  }

  private static EntitlementEvent prepareEntitlementEvent(ApplicationStageContext ctx, EntitlementType type) {
    return new EntitlementEvent(type.name(), ctx.getModuleId(), ctx.getTenantName(), ctx.getTenantId());
  }
}
