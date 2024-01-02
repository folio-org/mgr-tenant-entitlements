package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.repository.EntitlementFlowRepository;
import org.folio.flow.api.StageContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
public abstract class AbstractFlowFinalizer extends DatabaseLoggingStage {

  private EntitlementFlowRepository entitlementFlowRepository;

  @Override
  @Transactional
  public void execute(StageContext context) {
    var entitlementFlowId = getEntitlementFlowId(context);
    var entitlementFlowEntity = entitlementFlowRepository.getReferenceById(entitlementFlowId);
    entitlementFlowEntity.setStatus(EntityExecutionStatus.from(getStatus()));
    entitlementFlowRepository.save(entitlementFlowEntity);
  }

  @Autowired
  public void setEntitlementFlowRepository(EntitlementFlowRepository entitlementFlowRepository) {
    this.entitlementFlowRepository = entitlementFlowRepository;
  }

  protected abstract ExecutionStatus getStatus();
}
