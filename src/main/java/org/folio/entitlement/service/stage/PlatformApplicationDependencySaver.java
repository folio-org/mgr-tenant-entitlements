package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.entitlement.service.PlatformApplicationDependencyService;
import org.springframework.stereotype.Component;

/**
 * Saves dependencies of a platform application, the platform counterpart of {@link ApplicationDependencySaver}. An
 * application that is already entitled keeps its dependencies: they are neither saved again nor deleted on cancel.
 */
@Component
@RequiredArgsConstructor
public class PlatformApplicationDependencySaver extends DatabaseLoggingStage<ApplicationStageContext> {

  private final PlatformApplicationDependencyService dependencyService;
  private final PlatformEntitlementRepository entitlementRepository;

  @Override
  public void execute(ApplicationStageContext context) {
    var applicationId = context.getApplicationId();
    var alreadyEntitled = entitlementRepository.existsById(SemverUtils.getName(applicationId));

    context.withPlatformDependenciesPreexisting(alreadyEntitled);

    if (!alreadyEntitled) {
      dependencyService.saveDependencies(applicationId, context.getApplicationDescriptor().getDependencies());
    }
  }

  @Override
  public void cancel(ApplicationStageContext context) {
    if (!context.isPlatformDependenciesPreexisting()) {
      dependencyService.deleteDependencies(context.getApplicationId(),
        context.getApplicationDescriptor().getDependencies());
    }
  }
}
