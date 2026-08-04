package org.folio.entitlement.service.flow;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.service.stage.CancellationFailedFlowFinalizer;
import org.folio.entitlement.service.stage.CancelledFlowFinalizer;
import org.folio.entitlement.service.stage.FailedFlowFinalizer;
import org.folio.flow.api.Flow.FlowBuilder;
import org.springframework.stereotype.Component;

/**
 * Applies the standard finalizer callback set to a flow, so a factory cannot silently omit one of them.
 */
@Component
@RequiredArgsConstructor
public class FlowFinalizerCallbacks {

  private final FailedFlowFinalizer failedFlowFinalizer;
  private final CancelledFlowFinalizer cancelledFlowFinalizer;
  private final CancellationFailedFlowFinalizer cancellationFailedFlowFinalizer;

  public FlowBuilder apply(FlowBuilder builder) {
    return builder
      .onFlowError(failedFlowFinalizer)
      .onFlowCancellation(cancelledFlowFinalizer)
      .onFlowCancellationError(cancellationFailedFlowFinalizer);
  }
}
