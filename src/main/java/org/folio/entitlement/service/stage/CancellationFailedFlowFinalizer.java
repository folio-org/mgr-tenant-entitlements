package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.ExecutionStatus.CANCELLATION_FAILED;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CancellationFailedFlowFinalizer extends AbstractFlowFinalizer {

  @Override
  protected ExecutionStatus getStatus() {
    return CANCELLATION_FAILED;
  }
}
