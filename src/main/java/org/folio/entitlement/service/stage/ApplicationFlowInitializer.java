package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ApplicationFlowInitializer extends DatabaseLoggingStage<ApplicationStageContext> {

  private final ApplicationFlowRepository applicationFlowRepository;

  @Override
  @Transactional
  public void execute(ApplicationStageContext context) {
    var entitlementFlowId = context.getCurrentFlowId();
    var entitlementFlowEntity = applicationFlowRepository.getReferenceById(entitlementFlowId);
    entitlementFlowEntity.setStatus(EntityExecutionStatus.IN_PROGRESS);
    applicationFlowRepository.save(entitlementFlowEntity);
  }
}
