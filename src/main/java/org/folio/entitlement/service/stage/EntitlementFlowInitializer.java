package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.repository.EntitlementFlowRepository;
import org.folio.flow.api.StageContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class EntitlementFlowInitializer extends DatabaseLoggingStage {

  private final EntitlementFlowRepository entitlementFlowRepository;

  @Override
  @Transactional
  public void execute(StageContext context) {
    var entitlementFlowId = getEntitlementFlowId(context);
    var entitlementFlowEntity = entitlementFlowRepository.getReferenceById(entitlementFlowId);
    entitlementFlowEntity.setStatus(EntityExecutionStatus.IN_PROGRESS);
    entitlementFlowRepository.save(entitlementFlowEntity);
  }
}
