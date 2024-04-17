package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;

import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.integration.folio.CommonStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.FlowRepository;
import org.springframework.stereotype.Component;

@Component
public class FinishedFlowFinalizer extends AbstractFlowFinalizer<FlowEntity, CommonStageContext> {

  /**
   * Injects beans from spring context.
   *
   * @param flowRepository - {@link ApplicationFlowRepository} bean
   */
  public FinishedFlowFinalizer(FlowRepository flowRepository) {
    super(flowRepository);
  }

  @Override
  protected ExecutionStatus getFinalStatus() {
    return FINISHED;
  }
}
