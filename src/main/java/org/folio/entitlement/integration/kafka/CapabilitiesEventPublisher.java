package org.folio.entitlement.integration.kafka;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.folio.entitlement.integration.kafka.model.ModuleType.MODULE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class CapabilitiesEventPublisher extends DatabaseLoggingStage<ApplicationStageContext> {

  private final CapabilitiesModuleEventPublisher capabilitiesModuleEventPublisher;

  @Override
  public void execute(ApplicationStageContext context) {
    var appDesc = context.getApplicationDescriptor();
    var tenant = context.getTenantName();
    var appId = appDesc.getId();

    for (var moduleDescriptor : emptyIfNull(appDesc.getModuleDescriptors())) {
      capabilitiesModuleEventPublisher.sendEvent(moduleDescriptor, appId, tenant, MODULE);
    }

    for (var desc : emptyIfNull(appDesc.getUiModuleDescriptors())) {
      capabilitiesModuleEventPublisher.sendEvent(desc, appId, tenant, UI_MODULE);
    }
  }
}
