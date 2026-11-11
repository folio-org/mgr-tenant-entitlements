package org.folio.entitlement.service.validator;

import static java.lang.String.format;
import static java.util.stream.Collectors.toMap;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.repository.ApplicationDependencyRepository;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

/**
 * Checks that every upgraded platform application version still satisfies the dependency range of every dependent
 * application, across all tenants. Any violation rejects the whole upgrade before anything is written.
 *
 * <p>Optional dependencies are skipped. The {@code optional} flag belongs to the dependent application and says it works
 * without the parent, so a parent version outside the declared range does not make the dependent inconsistent. Only
 * required dependencies block an upgrade. This matches {@code PlatformDependencyValidator}, which does not require
 * optional dependencies to be entitled either.</p>
 */
@Component
@RequiredArgsConstructor
public class PlatformTenantDependentsValidator extends DatabaseLoggingStage<CommonStageContext> {

  private final ApplicationDependencyRepository dependencyRepository;

  @Override
  public void execute(CommonStageContext context) {
    var versionsByName = toStream(context.getEntitlementRequest().getApplications())
      .collect(toMap(SemverUtils::getName, SemverUtils::getVersion));
    var violations = toStream(dependencyRepository.findByParentNameIn(List.copyOf(versionsByName.keySet())))
      .filter(dependency -> !Boolean.TRUE.equals(dependency.getOptional()))
      .filter(dependency -> !SemverUtils.satisfies(versionsByName.get(dependency.getParentName()),
        dependency.getParentVersion()))
      .map(dependency -> new Parameter()
        .key(format("%s:%s", dependency.getTenantId(), dependency.getApplicationId()))
        .value(format("Requires %s %s, which the upgrade to %s does not satisfy", dependency.getParentName(),
          dependency.getParentVersion(), versionsByName.get(dependency.getParentName()))))
      .toList();

    if (!violations.isEmpty()) {
      throw new RequestValidationException("Platform application upgrade is incompatible with dependent applications",
        violations);
    }
  }
}
