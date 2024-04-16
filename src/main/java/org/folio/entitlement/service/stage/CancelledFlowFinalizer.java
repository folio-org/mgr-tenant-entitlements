package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.ExecutionStatus.CANCELLED;

import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.springframework.stereotype.Component;

@Component
public class CancelledFlowFinalizer extends FlowFinalizer {

  /**
   * Injects beans from spring context.
   *
   * @param flowRepository - {@link ApplicationFlowRepository} bean
   * @param applicationFlowService - {@link ApplicationFlowService} bean
   */
  public CancelledFlowFinalizer(FlowRepository flowRepository, ApplicationFlowService applicationFlowService) {
    super(flowRepository, applicationFlowService);
  }

  @Override
  protected ExecutionStatus getFinalStatus() {
    return CANCELLED;
  }
}
