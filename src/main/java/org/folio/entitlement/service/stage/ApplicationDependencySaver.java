package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.folio.ApplicationStageContext;
import org.folio.entitlement.service.ApplicationDependencyService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationDependencySaver extends DatabaseLoggingStage<ApplicationStageContext> {

  private final ApplicationDependencyService applicationDependencyService;

  @Override
  public void execute(ApplicationStageContext context) {
    applicationDependencyService.saveEntitlementDependencies(
      context.getTenantId(),
      context.getApplicationId(),
      context.getApplicationDescriptor().getDependencies());
  }

  @Override
  public void cancel(ApplicationStageContext context) {
    applicationDependencyService.deleteEntitlementDependencies(
      context.getTenantId(),
      context.getApplicationId(),
      context.getApplicationDescriptor().getDependencies());
  }
}
