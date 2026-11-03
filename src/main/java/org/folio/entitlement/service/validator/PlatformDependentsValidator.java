package org.folio.entitlement.service.validator;

import static java.lang.String.format;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.model.PlatformStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.repository.ApplicationDependencyRepository;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

/**
 * Checks that the upgraded platform application version still satisfies the dependency range of every dependent
 * application, across all tenants. Any violation rejects the whole upgrade before anything is written.
 */
@Component
@RequiredArgsConstructor
public class PlatformDependentsValidator extends DatabaseLoggingStage<PlatformStageContext> {

  private final ApplicationDependencyRepository dependencyRepository;

  @Override
  public void execute(PlatformStageContext context) {
    var applicationId = context.getPlatformRequest().getApplicationId();
    var version = SemverUtils.getVersion(applicationId);
    var violations = dependencyRepository.findByParentNameIn(List.of(SemverUtils.getName(applicationId))).stream()
      .filter(dependency -> !SemverUtils.satisfies(version, dependency.getParentVersion()))
      .map(dependency -> new Parameter()
        .key(format("%s:%s", dependency.getTenantId(), dependency.getApplicationId()))
        .value(format("Requires parent version %s, which the upgrade to %s does not satisfy",
          dependency.getParentVersion(), version)))
      .toList();

    if (!violations.isEmpty()) {
      throw new RequestValidationException("Platform application upgrade is incompatible with dependent applications",
        violations);
    }
  }
}
