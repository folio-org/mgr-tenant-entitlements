package org.folio.entitlement.service.validator;

import lombok.RequiredArgsConstructor;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.PlatformStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.exception.RequestValidationException.Params;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.semver4j.Semver;
import org.springframework.stereotype.Component;

/**
 * Requires some version of the application to be entitled and the requested version to be higher than it. The same
 * version is accepted: it is a retry, and the commit stage writes nothing for it.
 */
@Component
@RequiredArgsConstructor
public class PlatformUpgradeValidator extends DatabaseLoggingStage<PlatformStageContext> {

  private final PlatformEntitlementRepository repository;

  @Override
  public void execute(PlatformStageContext context) {
    var applicationId = context.getPlatformRequest().getApplicationId();
    var entitled = repository.findById(SemverUtils.getName(applicationId))
      .map(PlatformEntitlementEntity::getApplicationId)
      .orElseThrow(() -> new RequestValidationException("No version of the application is entitled to upgrade",
        new Params().add("applicationId", applicationId)));

    if (!entitled.equals(applicationId)) {
      validateHigherVersion(applicationId, entitled);
    }
  }

  private static void validateHigherVersion(String applicationId, String entitledApplicationId) {
    var requestedVersion = Semver.parse(SemverUtils.getVersion(applicationId));
    var entitledVersion = Semver.parse(SemverUtils.getVersion(entitledApplicationId));
    if (requestedVersion == null || entitledVersion == null || requestedVersion.compareTo(entitledVersion) <= 0) {
      throw new RequestValidationException("Application version is invalid or not higher than entitled",
        new Params().add("applicationId", applicationId).add("entitledApplicationId", entitledApplicationId));
    }
  }
}
