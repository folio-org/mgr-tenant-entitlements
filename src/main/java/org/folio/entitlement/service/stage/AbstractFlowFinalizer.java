package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.entity.AbstractFlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.repository.AbstractFlowRepository;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public abstract class AbstractFlowFinalizer<T extends AbstractFlowEntity, C extends IdentifiableStageContext>
  extends DatabaseLoggingStage<C> {

  private final AbstractFlowRepository<T> abstractFlowRepository;
  private final FlowFinalizerStatusProvider<C> statusProvider;

  @Override
  @Transactional
  public void execute(C context) {
    var entitlementFlowId = context.getCurrentFlowId();
    var entitlementFlowEntity = abstractFlowRepository.getReferenceById(entitlementFlowId);

    var prevStatus = entitlementFlowEntity.getStatus();
    var status = EntityExecutionStatus.from(statusProvider.getFinalStatus(context));

    if (!status.equals(prevStatus)) {
      entitlementFlowEntity.setStatus(status);
      abstractFlowRepository.save(entitlementFlowEntity);
      afterFlowStatusUpdate(context);
    }
  }

  protected void afterFlowStatusUpdate(C context) {}
}
