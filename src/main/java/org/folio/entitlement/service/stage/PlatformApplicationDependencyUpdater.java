package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.PlatformApplicationDependencyService;
import org.springframework.stereotype.Component;

/**
 * Replaces the dependencies of the entitled platform application version with the dependencies of the new version, the
 * platform counterpart of {@link ApplicationDependencyUpdater}. Upgrading to the entitled version is a retry: its
 * dependencies are kept, and they are not changed on cancel.
 */
@Component
@RequiredArgsConstructor
public class PlatformApplicationDependencyUpdater extends DatabaseLoggingStage<ApplicationStageContext> {

  private final PlatformApplicationDependencyService dependencyService;

  @Override
  public void execute(ApplicationStageContext context) {
    var sameVersion = context.getEntitledApplicationId().equals(context.getApplicationId());
    context.withPlatformDependenciesPreexisting(sameVersion);

    if (!sameVersion) {
      replaceDependencies(context.getEntitledApplicationId(), context.getEntitledApplicationDescriptor(),
        context.getApplicationId(), context.getApplicationDescriptor());
    }
  }

  @Override
  public void cancel(ApplicationStageContext context) {
    if (!context.isPlatformDependenciesPreexisting()) {
      replaceDependencies(context.getApplicationId(), context.getApplicationDescriptor(),
        context.getEntitledApplicationId(), context.getEntitledApplicationDescriptor());
    }
  }

  private void replaceDependencies(String fromId, ApplicationDescriptor from,
    String toId, ApplicationDescriptor to) {
    dependencyService.deleteDependencies(fromId, from.getDependencies());
    dependencyService.saveDependencies(toId, to.getDependencies());
  }
}
