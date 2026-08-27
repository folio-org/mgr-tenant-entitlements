package org.folio.entitlement.service.flow;

import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single entry point for completing a flow once its asynchronous stage confirmations have arrived.
 *
 * <p>Two callers reach this service, and both are needed:</p>
 * <ul>
 *   <li>{@link org.folio.entitlement.integration.kafka.ResourceResultEventService} - when a result delivers the
 *   last outstanding confirmation after the finalizer has already deferred.</li>
 *   <li>{@code AbstractFlowFinalizer} - after its own stage row becomes terminal, for the case where every
 *   confirmation had already arrived before the finalizer ran. Without this the finalizer's in-line check and an
 *   in-flight result could each observe the other as pending and neither would complete the flow.</li>
 * </ul>
 *
 * <p>Both paths are safe because the underlying statements are compare-and-set and additionally require the flow
 * to carry an {@code awaitingAsyncSince} anchor: a flow whose finalizer has not run yet cannot be completed, no
 * matter how quickly a downstream service answers. Running this twice is therefore harmless - the second call
 * updates zero rows.</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class FlowCompletionService {

  private final ApplicationFlowService applicationFlowService;
  private final FlowService flowService;

  /**
   * Completes the flow identified by {@code flowId} and, when that was the last outstanding one, its parent flow.
   *
   * @param flowId - application flow identifier, or a top-level flow identifier
   * @param finishedAt - timestamp to stamp on the completed rows
   */
  @Transactional
  public void completeIfNoActiveStages(UUID flowId, ZonedDateTime finishedAt) {
    if (applicationFlowService.finishFlowIfNoActiveStages(flowId, finishedAt) > 0) {
      log.info("Application flow marked as 'Finished', no async confirmations remain [applicationFlowId: {}]", flowId);
    }

    var topLevelFlowId = flowService.getTopLevelFlow(flowId).getId();
    if (flowService.finishFlowIfNoActiveStages(topLevelFlowId, finishedAt) > 0) {
      log.info("Flow marked as 'Finished', no async confirmations remain [flowId: {}]", topLevelFlowId);
    }
  }

  /**
   * Fails the flow identified by {@code flowId} and its parent flow.
   *
   * <p>Unlike completion this is not gated on the anchor. Failing early is the correct outcome regardless of how
   * much of the flow has run - it matches what the execution timeout already does, and it stops further
   * application flows from starting rather than letting them proceed behind a known failure.</p>
   *
   * @param flowId - application flow identifier, or a top-level flow identifier
   * @param finishedAt - timestamp to stamp on the failed rows
   */
  @Transactional
  public void failFlows(UUID flowId, ZonedDateTime finishedAt) {
    if (applicationFlowService.failActiveFlow(flowId, finishedAt) > 0) {
      log.warn("Application flow marked as 'Failed' by a downstream result [applicationFlowId: {}]", flowId);
    }

    var topLevelFlowId = flowService.getTopLevelFlow(flowId).getId();
    if (flowService.failActiveFlow(topLevelFlowId, finishedAt) > 0) {
      log.warn("Flow marked as 'Failed' by a downstream result [flowId: {}]", topLevelFlowId);
    }
  }
}
