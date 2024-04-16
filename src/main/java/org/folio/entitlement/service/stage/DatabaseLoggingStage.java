package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLATION_FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.utils.EntitlementServiceUtils.getErrorMessage;

import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.entity.FlowStageEntity;
import org.folio.entitlement.domain.entity.key.FlowStageKey;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.integration.folio.IdentifiableStageContext;
import org.folio.entitlement.repository.FlowStageRepository;
import org.folio.flow.api.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
public abstract class DatabaseLoggingStage<C extends IdentifiableStageContext> implements Stage<C> {

  protected FlowStageRepository stageRepository;

  @Override
  @Transactional
  public void onStart(C context) {
    var entity = new FlowStageEntity();
    entity.setFlowId(context.getCurrentFlowId());
    entity.setStageName(getStageName(context));
    entity.setStatus(IN_PROGRESS);
    stageRepository.save(entity);
  }

  @Override
  @Transactional
  public void onSuccess(C context) {
    setEntitlementStageStatus(context, FINISHED, null);
  }

  @Override
  @Transactional
  public void onCancel(C context) {
    setEntitlementStageStatus(context, CANCELLED, null);
  }

  @Override
  @Transactional
  public void onCancelError(C context, Exception exception) {
    setEntitlementStageStatus(context, CANCELLATION_FAILED, exception);
  }

  @Override
  @Transactional
  public void onError(C context, Exception exception) {
    setEntitlementStageStatus(context, FAILED, exception);
  }

  @Autowired
  public void setStageRepository(FlowStageRepository flowStageRepository) {
    this.stageRepository = flowStageRepository;
  }

  @Override
  public String getId() {
    return this.getClass().getSimpleName();
  }

  public String getStageName(C context) {
    return getId();
  }

  private void setEntitlementStageStatus(C context, EntityExecutionStatus status, Exception error) {
    var stageExecutionKey = FlowStageKey.of(context.getCurrentFlowId(), getStageName(context));
    var stageExecutionEntity = stageRepository.getReferenceById(stageExecutionKey);
    stageExecutionEntity.setStatus(status);

    if (error != null) {
      stageExecutionEntity.setErrorType(error.getClass().getSimpleName());
      stageExecutionEntity.setErrorMessage(getErrorMessage(error));
    }

    stageRepository.save(stageExecutionEntity);
  }
}
