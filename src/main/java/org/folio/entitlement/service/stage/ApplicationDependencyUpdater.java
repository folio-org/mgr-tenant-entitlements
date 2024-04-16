package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.folio.ApplicationStageContext;
import org.folio.entitlement.service.ApplicationDependencyService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationDependencyUpdater extends DatabaseLoggingStage<ApplicationStageContext> {

  private final ApplicationDependencyService applicationDependencyService;

  @Override
  public void execute(ApplicationStageContext context) {
    var tenantId = context.getTenantId();
    applicationDependencyService.deleteEntitlementDependencies(
      tenantId, context.getEntitledApplicationId(), context.getEntitledApplicationDescriptor().getDependencies());

    applicationDependencyService.saveEntitlementDependencies(
      tenantId, context.getApplicationId(), context.getApplicationDescriptor().getDependencies());
  }
}
