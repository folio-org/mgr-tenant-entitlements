package org.folio.entitlement.service.stage;

import java.time.Instant;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.service.flow.FlowService;
import org.folio.entitlement.service.lock.PlatformLockService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FlowInitializer extends DatabaseLoggingStage<CommonStageContext> {

  private final FlowService flowService;
  private final PlatformLockService platformLockService;

  @Override
  public void execute(CommonStageContext context) {
    var entitlementRequest = context.getEntitlementRequest();
    var flow = new Flow()
      .id(context.getCurrentFlowId())
      .tenantId(entitlementRequest.getTenantId())
      .status(ExecutionStatus.IN_PROGRESS)
      .type(entitlementRequest.getType())
      .startedAt(Date.from(Instant.now()));

    flowService.create(flow);

    // Race: a platform operation may take the lock and read the flow table before this row is committed, so it misses
    // this flow. Re-check the lock now: if it is held, this tenant flow fails instead of running alongside it.
    if (entitlementRequest.getTenantId() != null) {
      platformLockService.assertNotHeld();
    }
  }
}
