package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLATION_FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.NON_TERMINAL_STATUSES;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Set;
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
   * Sets the final flow status with a single compare-and-set statement and runs {@link #afterFlowStatusUpdate(C)}
   * only when the status was actually written.
   *
   * <p>A timed-out flow is failed by {@code FlowService#failIfNotTerminal}, but the flow engine cannot abort it, so
   * it keeps running and eventually reaches this stage. The compare-and-set keeps the already reported status (and
   * skips the finalizer's side effects) - a plain read-check-save would race with the timeout update and could
   * overwrite it.</p>
   */
  @Override
  @Transactional
  public void execute(C context) {
    var entitlementFlowId = context.getCurrentFlowId();
    var status = EntityExecutionStatus.from(getFinalStatus());
    var updated = abstractFlowRepository.updateStatusIfCurrentIn(
      entitlementFlowId, status, allowedCurrentStatuses(status), ZonedDateTime.now(ZoneId.systemDefault()));

    if (updated == 0) {
      log.warn("Flow status update to {} is skipped, flow is already in a terminal status [flowId: {}]",
        status, entitlementFlowId);
      return;
    }

    afterFlowStatusUpdate(context);
  }

  protected abstract ExecutionStatus getFinalStatus();

  protected void afterFlowStatusUpdate(C context) {}

  /**
   * Cancellation must be able to roll back a FINISHED flow and to supersede a timeout-forced FAILED status;
   * FINISHED/FAILED must not overwrite a status already reported to the caller.
   */
  private static Set<EntityExecutionStatus> allowedCurrentStatuses(EntityExecutionStatus target) {
    return target == CANCELLED || target == CANCELLATION_FAILED
      ? EnumSet.allOf(EntityExecutionStatus.class)
      : NON_TERMINAL_STATUSES;
  }
}
