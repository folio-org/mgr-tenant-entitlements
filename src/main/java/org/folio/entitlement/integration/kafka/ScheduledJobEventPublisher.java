package org.folio.entitlement.integration.kafka;

import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduledJobEventPublisher extends DatabaseLoggingStage<ApplicationStageContext> {

  private final ScheduledJobModuleEventPublisher scheduledJobModuleEventPublisher;

  @Override
  public void execute(ApplicationStageContext context) {
    var tenantId = context.getTenantId();
    var tenantName = context.getTenantName();
    var moduleDescriptors = emptyIfNull(context.getApplicationDescriptor().getModuleDescriptors());

    for (var moduleDescriptor : moduleDescriptors) {
      scheduledJobModuleEventPublisher.sendEvent(tenantId, tenantName, moduleDescriptor);
    }
  }
}
