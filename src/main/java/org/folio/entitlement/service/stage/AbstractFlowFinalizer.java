package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.AbstractFlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.repository.AbstractFlowRepository;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@RequiredArgsConstructor
public abstract class AbstractFlowFinalizer<T extends AbstractFlowEntity, C extends IdentifiableStageContext>
  extends DatabaseLoggingStage<C> {

  private final AbstractFlowRepository<T> abstractFlowRepository;

  /**
   * Sets the final flow status, unless the flow has already reached a terminal one.
   *
   * <p>A timed-out flow is failed by {@code FlowService#failIfNotTerminal}, but the flow engine cannot abort it, so it
   * keeps running and eventually reaches this stage. The already reported status wins - otherwise it would be replaced
   * by the outcome of an execution the caller was told had failed.</p>
   */
  @Override
  @Transactional
  public void execute(C context) {
    var entitlementFlowId = context.getCurrentFlowId();
    var entitlementFlowEntity = abstractFlowRepository.getReferenceById(entitlementFlowId);

    if (EntityExecutionStatus.isTerminal(entitlementFlowEntity.getStatus())) {
      log.warn("Flow status update is skipped, flow is already in a terminal status [flowId: {}, status: {}]",
        entitlementFlowId, entitlementFlowEntity.getStatus());
    } else {
      entitlementFlowEntity.setStatus(EntityExecutionStatus.from(getFinalStatus()));
      abstractFlowRepository.save(entitlementFlowEntity);
    }

    afterFlowStatusUpdate(context);
  }

  protected abstract ExecutionStatus getFinalStatus();

  protected void afterFlowStatusUpdate(C context) {}
}
