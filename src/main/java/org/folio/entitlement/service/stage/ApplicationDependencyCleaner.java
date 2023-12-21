package org.folio.entitlement.service.stage;

import static org.folio.entitlement.service.stage.StageContextUtils.getApplicationDescriptor;
import static org.folio.entitlement.service.stage.StageContextUtils.getApplicationId;
import static org.folio.entitlement.service.stage.StageContextUtils.getEntitlementRequest;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.service.ApplicationDependencyService;
import org.folio.flow.api.Cancellable;
import org.folio.flow.api.StageContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationDependencyCleaner extends DatabaseLoggingStage implements Cancellable {

  private final ApplicationDependencyService applicationDependencyService;

  @Override
  public void execute(StageContext context) {
    applicationDependencyService.deleteEntitlementDependencies(
      getEntitlementRequest(context).getTenantId(),
      getApplicationId(context),
      getApplicationDescriptor(context).getDependencies());
  }

  @Override
  public void cancel(StageContext context) {
    applicationDependencyService.saveEntitlementDependencies(
      getEntitlementRequest(context).getTenantId(),
      getApplicationId(context),
      getApplicationDescriptor(context).getDependencies());
  }
}
