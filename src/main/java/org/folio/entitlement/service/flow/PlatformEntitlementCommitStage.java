package org.folio.entitlement.service.flow;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.PlatformStageContext;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the entitled version. The application name is the key, so an upgrade replaces the entitled row in place. A
 * retry whose version is already entitled saves identical state, which changes nothing, so the commit is idempotent.
 */
@Component
@RequiredArgsConstructor
public class PlatformEntitlementCommitStage extends DatabaseLoggingStage<PlatformStageContext> {

  private final PlatformEntitlementRepository repository;

  @Override
  @Transactional
  public void execute(PlatformStageContext context) {
    var applicationId = context.getPlatformRequest().getApplicationId();
    repository.save(PlatformEntitlementEntity.of(applicationId));
  }
}
