package org.folio.entitlement.service.flow;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.model.PlatformEntitlementRequest;
import org.folio.entitlement.service.lock.PlatformLockService;
import org.folio.flow.api.FlowEngine;
import org.springframework.stereotype.Service;

/**
 * Entitles or upgrades a platform-scoped application deployment-wide.
 *
 * <p>The platform lock is taken before the flow record is created, so a request rejected because another platform
 * operation is running leaves no flow row. The lock is released on every outcome.</p>
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
    log.info("Performing platform entitlement request: applicationId = {}, type = {}",
      request.getApplicationId(), request.getType());

    var flowId = UUID.randomUUID();
    platformLockService.acquire(flowId);
    try {
      flowService.create(new Flow()
        .id(flowId)
        .status(ExecutionStatus.IN_PROGRESS)
        .type(request.getType())
        .startedAt(Date.from(Instant.now())));

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
