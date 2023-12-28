package org.folio.entitlement.service.stage;

import static org.folio.entitlement.service.stage.StageContextUtils.getApplicationId;
import static org.folio.entitlement.service.stage.StageContextUtils.getEntitlementRequest;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.flow.api.StageContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RevokeFlowFinalizer extends AbstractFlowFinalizer {

  private final EntitlementCrudService entitlementCrudService;

  @Override
  @Transactional
  public void execute(StageContext context) {
    super.execute(context);
    var request = getEntitlementRequest(context);
    var applicationId = getApplicationId(context);
    var entitlement = new Entitlement().applicationId(applicationId).tenantId(request.getTenantId());
    entitlementCrudService.delete(entitlement);
  }

  @Override
  protected ExecutionStatus getStatus() {
    return ExecutionStatus.FINISHED;
  }
}
