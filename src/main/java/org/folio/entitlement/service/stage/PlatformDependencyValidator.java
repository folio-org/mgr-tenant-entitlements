package org.folio.entitlement.service.stage;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.folio.common.utils.CollectionUtils.toStream;

import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.Dependency;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.springframework.stereotype.Component;

/**
 * Requires every non-optional dependency of a platform application to be entitled with a version that satisfies the
 * dependency range.
 */
@Component
@RequiredArgsConstructor
public class PlatformDependencyValidator extends DatabaseLoggingStage<ApplicationStageContext> {

  private final PlatformEntitlementRepository repository;

  @Override
  public void execute(ApplicationStageContext context) {
    var required = toStream(context.getApplicationDescriptor().getDependencies())
      .filter(dependency -> !Boolean.TRUE.equals(dependency.getOptional()))
      .toList();

    if (required.isEmpty()) {
      return;
    }

    var requiredNames = required.stream().map(Dependency::getName).distinct().toList();
    var entitledVersions = repository.findByApplicationNameIn(requiredNames).stream()
      .collect(toMap(PlatformEntitlementEntity::getApplicationName, PlatformEntitlementEntity::getApplicationVersion));

    var notEntitled = required.stream()
      .filter(dependency -> !isSatisfied(dependency, entitledVersions.get(dependency.getName())))
      .map(Dependency::nameVersion)
      .sorted()
      .collect(joining(", "));

    if (!notEntitled.isEmpty()) {
      throw new RequestValidationException(
        "The following platform application dependencies must be entitled first", "dependencies", notEntitled);
    }
  }

  private static boolean isSatisfied(Dependency dependency, String entitledVersion) {
    return entitledVersion != null && SemverUtils.satisfies(entitledVersion, dependency.getVersion());
  }
}
