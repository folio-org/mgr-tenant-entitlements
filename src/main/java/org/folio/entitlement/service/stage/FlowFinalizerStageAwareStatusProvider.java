package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.AbstractFlowEntity;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.repository.AbstractFlowRepository;

@RequiredArgsConstructor
public class FlowFinalizerStageAwareStatusProvider<T extends AbstractFlowEntity, C extends IdentifiableStageContext>
  implements FlowFinalizerStatusProvider<C> {

  private final AbstractFlowRepository<T> flowRepository;

  @Override
  public ExecutionStatus getFinalStatus(C context) {
    var entitlementFlowId = context.getCurrentFlowId();
    var isInProgress = flowRepository.existsAnyStageByFlowIdAndStatusExcluding(
      entitlementFlowId, IN_PROGRESS, context.getStageId());
    return isInProgress ? ExecutionStatus.IN_PROGRESS : ExecutionStatus.FINISHED;
  }
}
