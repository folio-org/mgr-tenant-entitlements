package org.folio.entitlement.integration.folio.stage;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.utils.EntitlementServiceUtils.isModuleVersionChanged;

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
    var moduleDescriptor = context.getModuleDescriptor();
    var installedModuleDescriptor = context.getInstalledModuleDescriptor();

    if (!isModuleVersionChanged(moduleDescriptor, installedModuleDescriptor)) {
      return;
    }

    if (moduleDescriptor == null) {
      publishEvent(getEntitlementEvent(installedModuleDescriptor.getId(), context, REVOKE));
      return;
    }

    publishEvent(getEntitlementEvent(moduleDescriptor.getId(), context, request.getType()));
    if (installedModuleDescriptor != null) {
      publishEvent(getEntitlementEvent(installedModuleDescriptor.getId(), context, REVOKE));
    }
  }

  @Override
  public void cancel(ModuleStageContext context) {
    var request = context.getEntitlementRequest();
    if (request.getType() != ENTITLE) {
      return;
    }

    publishEvent(getEntitlementEvent(context.getModuleId(), context, REVOKE));
  }

  private void publishEvent(EntitlementEvent event) {
    publisher.publish(event);
    log.debug("Published event: event = {}", event);
  }

  private static EntitlementEvent getEntitlementEvent(String moduleId, ModuleStageContext ctx, EntitlementType type) {
    return new EntitlementEvent(type.name(), moduleId, ctx.getTenantName(), ctx.getTenantId());
  }
}
