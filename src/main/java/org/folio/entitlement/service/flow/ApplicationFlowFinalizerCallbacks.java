package org.folio.entitlement.service.flow;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.service.stage.CancellationFailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.CancelledApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.FailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.SkippedApplicationFlowFinalizer;
import org.folio.flow.api.Flow.FlowBuilder;
import org.springframework.stereotype.Component;

/**
 * Applies the standard finalizer callback set to an application flow, so a factory cannot silently omit one of them.
 */
@Component
@RequiredArgsConstructor
public class ApplicationFlowFinalizerCallbacks {

  private final SkippedApplicationFlowFinalizer skippedFlowFinalizer;
  private final FailedApplicationFlowFinalizer failedFlowFinalizer;
  private final CancelledApplicationFlowFinalizer cancelledFlowFinalizer;
  private final CancellationFailedApplicationFlowFinalizer cancellationFailedFlowFinalizer;

  public FlowBuilder apply(FlowBuilder builder) {
    return builder
      .onFlowSkip(skippedFlowFinalizer)
      .onFlowError(failedFlowFinalizer)
      .onFlowCancellation(cancelledFlowFinalizer)
      .onFlowCancellationError(cancellationFailedFlowFinalizer);
  }
}
