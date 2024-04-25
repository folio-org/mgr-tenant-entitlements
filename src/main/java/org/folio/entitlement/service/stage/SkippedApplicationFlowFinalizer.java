package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.QUEUED;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class SkippedApplicationFlowFinalizer extends DatabaseLoggingStage<ApplicationStageContext> {

  private final ApplicationFlowRepository applicationFlowRepository;

  @Override
  @Transactional
  public void execute(ApplicationStageContext stageContext) {
    var flowUuid = stageContext.getCurrentFlowId();
    var flowEntity = applicationFlowRepository.getReferenceById(flowUuid);
    if (flowEntity.getStatus() == QUEUED) {
      applicationFlowRepository.delete(flowEntity);
    }
  }
}
