package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.AbstractFlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.repository.AbstractFlowRepository;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public abstract class AbstractFlowFinalizer<T extends AbstractFlowEntity, C extends IdentifiableStageContext>
  extends DatabaseLoggingStage<C> {

  private final AbstractFlowRepository<T> abstractFlowRepository;

  @Override
  @Transactional
  public void execute(C context) {
    var entitlementFlowId = context.getCurrentFlowId();
    var entitlementFlowEntity = abstractFlowRepository.getReferenceById(entitlementFlowId);
    entitlementFlowEntity.setStatus(EntityExecutionStatus.from(getFinalStatus()));
    abstractFlowRepository.save(entitlementFlowEntity);
    afterFlowStatusUpdate(context);
  }

  protected abstract ExecutionStatus getFinalStatus();

  protected void afterFlowStatusUpdate(C context) {}
}
