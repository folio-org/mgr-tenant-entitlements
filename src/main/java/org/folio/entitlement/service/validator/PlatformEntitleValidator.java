package org.folio.entitlement.service.validator;

import lombok.RequiredArgsConstructor;
import org.folio.common.utils.SemverUtils;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.PlatformStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.exception.RequestValidationException.Params;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

/**
 * Rejects an entitle request while a different version of the application is already entitled. The same version is
 * accepted: it is a retry, and the commit stage writes nothing for it.
 */
@Component
@RequiredArgsConstructor
public class PlatformEntitleValidator extends DatabaseLoggingStage<PlatformStageContext> {

  private final PlatformEntitlementRepository repository;

  @Override
  public void execute(PlatformStageContext context) {
    var applicationId = context.getPlatformRequest().getApplicationId();
    repository.findById(SemverUtils.getName(applicationId))
      .map(PlatformEntitlementEntity::getApplicationId)
      .filter(entitledApplicationId -> !entitledApplicationId.equals(applicationId))
      .ifPresent(entitledApplicationId -> {
        throw new RequestValidationException("Another version of the application is already entitled",
          new Params().add("applicationId", applicationId).add("entitledApplicationId", entitledApplicationId));
      });
  }
}
