package org.folio.entitlement.integration.folio.stage;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.kafka.EntitlementEventPublisher;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;

@Log4j2
@RequiredArgsConstructor
public class FolioModuleEventPublisher extends ModuleDatabaseLoggingStage {

  private final EntitlementEventPublisher publisher;

  @Override
  public void execute(ModuleStageContext context) {
    var request = context.getEntitlementRequest();
    var event = prepareEntitlementEvent(context, request.getType());
    publisher.publish(event);
    log.debug("Published event: event = {}", event);
  }

  @Override
  public void cancel(ModuleStageContext context) {
    var request = context.getEntitlementRequest();
    if (request.getType() != ENTITLE) {
      return;
    }

    var event = prepareEntitlementEvent(context, REVOKE);
    publisher.publish(event);
    log.debug("Published event: event = {}", event);
  }

  private static EntitlementEvent prepareEntitlementEvent(ModuleStageContext ctx, EntitlementType type) {
    return new EntitlementEvent(type.name(), ctx.getModuleId(), ctx.getTenantName(), ctx.getTenantId());
  }
}
