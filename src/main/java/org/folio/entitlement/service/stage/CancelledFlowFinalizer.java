package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.ExecutionStatus.CANCELLED;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CancelledFlowFinalizer extends AbstractFlowFinalizer {

  @Override
  protected ExecutionStatus getStatus() {
    return CANCELLED;
  }
}
