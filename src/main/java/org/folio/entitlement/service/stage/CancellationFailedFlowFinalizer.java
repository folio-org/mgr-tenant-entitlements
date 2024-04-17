package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.ExecutionStatus.CANCELLATION_FAILED;

import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.springframework.stereotype.Component;

@Component
public class CancellationFailedFlowFinalizer extends FlowFinalizer {

  /**
   * Injects beans from spring context.
   *
   * @param flowRepository - {@link ApplicationFlowRepository} bean
   * @param applicationFlowService - {@link ApplicationFlowService} bean
   */
  public CancellationFailedFlowFinalizer(FlowRepository flowRepository, ApplicationFlowService applicationFlowService) {
    super(flowRepository, applicationFlowService);
  }

  @Override
  protected ExecutionStatus getFinalStatus() {
    return CANCELLATION_FAILED;
  }
}
