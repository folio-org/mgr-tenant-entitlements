package org.folio.entitlement.service.validator;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.folio.common.utils.CollectionUtils.toStream;

import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

/**
 * Rejects an entitle request while a different version of any requested application is already entitled. The same
 * version is accepted: it is a retry, and the commit stage writes nothing new for it. Every violation is reported,
 * and one violation rejects the whole request.
 */
@Component
@RequiredArgsConstructor
public class PlatformExistEntitlementValidator extends DatabaseLoggingStage<CommonStageContext> {

  private final PlatformEntitlementRepository repository;

  @Override
  public void execute(CommonStageContext context) {
    var applicationIds = context.getEntitlementRequest().getApplications();
    var entitledNames = toStream(applicationIds).map(SemverUtils::getName).toList();
    var entitled = toStream(repository.findAllById(entitledNames))
      .collect(toMap(PlatformEntitlementEntity::getApplicationName, identity()));

    var violations = toStream(applicationIds)
      .filter(applicationId -> isOtherVersionEntitled(applicationId, entitled.get(SemverUtils.getName(applicationId))))
      .map(applicationId -> new Parameter().key(applicationId)
        .value("Another version is entitled: " + entitled.get(SemverUtils.getName(applicationId)).getApplicationId()))
      .toList();

    if (!violations.isEmpty()) {
      throw new RequestValidationException("Another version of the application is already entitled", violations);
    }
  }

  private static boolean isOtherVersionEntitled(String applicationId, PlatformEntitlementEntity entitled) {
    return entitled != null && !entitled.getApplicationId().equals(applicationId);
  }
}
