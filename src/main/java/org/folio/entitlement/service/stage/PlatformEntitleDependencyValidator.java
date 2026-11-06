package org.folio.entitlement.service.stage;

import static java.util.stream.Collectors.joining;
import static org.folio.common.utils.CollectionUtils.toStream;

import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.Dependency;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.springframework.stereotype.Component;

/**
 * Requires every non-optional dependency of a platform application to be entitled with a version that satisfies the
 * dependency range.
 */
@Component
@RequiredArgsConstructor
public class PlatformEntitleDependencyValidator extends DatabaseLoggingStage<ApplicationStageContext> {

  private final PlatformEntitlementRepository repository;

  @Override
  public void execute(ApplicationStageContext context) {
    var notEntitled = toStream(context.getApplicationDescriptor().getDependencies())
      .filter(dependency -> !Boolean.TRUE.equals(dependency.getOptional()))
      .filter(dependency -> !isEntitled(dependency))
      .map(Dependency::nameVersion)
      .sorted()
      .collect(joining(", "));

    if (!notEntitled.isEmpty()) {
      throw new IllegalStateException(
        "The following platform application dependencies must be entitled first: " + notEntitled);
    }
  }

  private boolean isEntitled(Dependency dependency) {
    return repository.findById(dependency.getName())
      .map(PlatformEntitlementEntity::getApplicationVersion)
      .filter(version -> SemverUtils.satisfies(version, dependency.getVersion()))
      .isPresent();
  }
}
