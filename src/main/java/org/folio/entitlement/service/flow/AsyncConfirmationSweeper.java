package org.folio.entitlement.service.flow;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class AsyncConfirmationSweeper {

  private final FlowService flowService;

  @Value("${application.async-confirmation.timeout:30m}")
  private Duration asyncConfirmationTimeout;

  /**
   * Fails flows that have been waiting for async stage confirmations longer than
   * {@code application.async-confirmation.timeout}. Each flow is failed in its own call so a single
   * database error does not abort the whole sweep.
   */
  @Scheduled(fixedDelayString = "${application.async-confirmation.sweep-interval:5m}")
  public void failStaleAsyncFlows() {
    var cutoff = ZonedDateTime.now(ZoneId.systemDefault()).minus(asyncConfirmationTimeout);
    var staleIds = flowService.findStaleAsyncFlowIds(cutoff);
    if (staleIds.isEmpty()) {
      return;
    }
    log.info("Async confirmation sweep: {} stale flow(s) found [cutoff: {}]", staleIds.size(), cutoff);
    for (var flowId : staleIds) {
      failStaleFlow(flowId);
    }
  }

  private void failStaleFlow(UUID flowId) {
    try {
      if (flowService.failIfNotTerminal(flowId)) {
        log.warn("Flow failed: async confirmation not received within {} [flowId: {}]",
          asyncConfirmationTimeout, flowId);
      }
    } catch (Exception e) {
      log.error("Failed to fail stale async flow [flowId: {}]", flowId, e);
    }
  }
}
