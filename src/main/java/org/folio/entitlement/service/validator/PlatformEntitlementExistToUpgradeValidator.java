package org.folio.entitlement.service.validator;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.semver4j.Semver;
import org.springframework.stereotype.Component;

/**
 * Requires some version of every requested application to be entitled and the requested version to be higher than
 * it. The same version is accepted: it is a retry, and the commit stage writes nothing new for it. Every violation is
 * reported, and one violation rejects the whole request.
 */
@Component
@RequiredArgsConstructor
public class PlatformEntitlementExistToUpgradeValidator extends DatabaseLoggingStage<CommonStageContext> {

  private final PlatformEntitlementRepository repository;

  @Override
  public void execute(CommonStageContext context) {
    var applicationIds = context.getEntitlementRequest().getApplications();
    var entitledNames = toStream(applicationIds).map(SemverUtils::getName).toList();
    var entitled = toStream(repository.findAllById(entitledNames))
      .collect(toMap(PlatformEntitlementEntity::getApplicationName, identity()));

    var violations = toStream(applicationIds)
      .map(applicationId -> validate(applicationId, entitled))
      .flatMap(Optional::stream)
      .toList();

    if (!violations.isEmpty()) {
      throw new RequestValidationException("Invalid applications provided for platform upgrade", violations);
    }
  }

  private static Optional<Parameter> validate(String applicationId, Map<String, PlatformEntitlementEntity> entitled) {
    var entitledEntity = entitled.get(SemverUtils.getName(applicationId));
    if (entitledEntity == null) {
      return violation(applicationId, "No version of the application is entitled to upgrade");
    }

    var entitledApplicationId = entitledEntity.getApplicationId();
    if (entitledApplicationId.equals(applicationId) || isHigher(applicationId, entitledApplicationId)) {
      return Optional.empty();
    }
    return violation(applicationId, "Version is invalid or not higher than entitled: " + entitledApplicationId);
  }

  private static boolean isHigher(String applicationId, String entitledApplicationId) {
    var requestedVersion = Semver.parse(SemverUtils.getVersion(applicationId));
    var entitledVersion = Semver.parse(SemverUtils.getVersion(entitledApplicationId));
    return requestedVersion != null && entitledVersion != null && requestedVersion.compareTo(entitledVersion) > 0;
  }

  private static Optional<Parameter> violation(String applicationId, String reason) {
    return Optional.of(new Parameter().key(applicationId).value(reason));
  }
}
