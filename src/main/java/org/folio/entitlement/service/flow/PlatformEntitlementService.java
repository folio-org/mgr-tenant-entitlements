package org.folio.entitlement.service.flow;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.PlatformEntitlementRequest;
import org.folio.entitlement.service.lock.PlatformLockService;
import org.folio.flow.api.FlowEngine;
import org.springframework.stereotype.Service;

/**
 * Entitles or upgrades a platform-scoped application deployment-wide.
 *
 * <p>The platform lock is taken before the flow starts, so a request rejected because another platform operation is
 * running leaves no flow row: the flow row is created by the flow's first stage. The lock is released on every
 * outcome.</p>
 */
@Log4j2
@Service
@RequiredArgsConstructor
public class PlatformEntitlementService {

  private final FlowEngine flowEngine;
  private final FlowService flowService;
  private final PlatformLockService platformLockService;
  private final PlatformFlowProvider flowProvider;

  /**
   * Entitles or upgrades a platform-scoped application. Synchronous: the lock is held for the whole call.
   *
   * @param request - platform entitlement request
   * @return identifier of the flow that performed the request
   */
  public UUID performRequest(PlatformEntitlementRequest request) {
    log.debug("Performing platform entitlement request: applicationIds = {}, type = {}",
      request.getApplicationIds(), request.getType());

    var flowId = UUID.randomUUID();
    platformLockService.acquire(flowId);
    try {
      flowEngine.execute(flowProvider.createFlow(flowId, request));
      flowService.finish(flowId);
      return flowId;
    } catch (RuntimeException exception) {
      flowService.failIfNotTerminal(flowId);
      throw exception;
    } finally {
      platformLockService.release(flowId);
    }
  }
}
