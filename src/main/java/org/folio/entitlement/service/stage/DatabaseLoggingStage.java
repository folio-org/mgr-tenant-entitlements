package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.ExecutionStatus.CANCELLATION_FAILED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.CANCELLED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FAILED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.utils.EntitlementServiceUtils.getErrorMessage;

import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.EntitlementStageEntity;
import org.folio.entitlement.domain.entity.key.EntitlementStageKey;
import org.folio.entitlement.repository.EntitlementStageRepository;
import org.folio.flow.api.Listenable;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
public abstract class DatabaseLoggingStage implements Listenable, Stage {

  protected EntitlementStageRepository stageRepository;

  @Override
  @Transactional
  public void onStart(StageContext context) {
    var entity = new EntitlementStageEntity();
    entity.setApplicationFlowId(getEntitlementFlowId(context));
    entity.setName(getStageName(context));
    entity.setStatus(IN_PROGRESS);
    stageRepository.save(entity);
  }

  @Override
  @Transactional
  public void onSuccess(StageContext context) {
    setEntitlementStageStatus(context, FINISHED, null);
  }

  @Override
  @Transactional
  public void onCancel(StageContext context) {
    setEntitlementStageStatus(context, CANCELLED, null);
  }

  @Override
  @Transactional
  public void onCancelError(StageContext context, Exception exception) {
    setEntitlementStageStatus(context, CANCELLATION_FAILED, exception);
  }

  @Override
  @Transactional
  public void onError(StageContext context, Exception exception) {
    setEntitlementStageStatus(context, FAILED, exception);
  }

  @Autowired
  public void setStageRepository(EntitlementStageRepository entitlementStageRepository) {
    this.stageRepository = entitlementStageRepository;
  }

  public String getStageName(StageContext context) {
    return this.getClass().getSimpleName();
  }

  protected UUID getEntitlementFlowId(StageContext context) {
    return UUID.fromString(context.flowId().split("/")[2]);
  }

  private void setEntitlementStageStatus(StageContext context, ExecutionStatus status, Exception exception) {
    var stageExecutionKey = EntitlementStageKey.of(getEntitlementFlowId(context), getStageName(context));
    var stageExecutionEntity = stageRepository.getReferenceById(stageExecutionKey);
    stageExecutionEntity.setStatus(status);

    if (exception != null) {
      stageExecutionEntity.setErrorType(exception.getClass().getSimpleName());
      stageExecutionEntity.setErrorMessage(getErrorMessage(exception));
    }

    stageRepository.save(stageExecutionEntity);
  }
}
