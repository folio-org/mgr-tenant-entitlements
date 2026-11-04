package org.folio.entitlement.service.flow;

import static org.folio.common.utils.CollectionUtils.toStream;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.PlatformStageContext;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the entitled versions of all requested applications in one transaction, so either all are committed or none.
 * The application name is the key, so an upgrade replaces the entitled row in place. A retry whose version is already
 * entitled saves identical state, which changes nothing, so the commit is idempotent.
 */
@Component
@RequiredArgsConstructor
public class PlatformEntitlementCommitStage extends DatabaseLoggingStage<PlatformStageContext> {

  private final PlatformEntitlementRepository repository;

  @Override
  @Transactional
  public void execute(PlatformStageContext context) {
    var entities = toStream(context.getPlatformRequest().getApplicationIds())
      .map(PlatformEntitlementEntity::of)
      .toList();
    repository.saveAll(entities);
  }
}
