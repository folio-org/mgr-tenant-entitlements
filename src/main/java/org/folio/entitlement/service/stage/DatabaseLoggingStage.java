package org.folio.entitlement.service.stage;

import static java.lang.String.format;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLATION_FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.model.ModuleStageContext.ATTR_RETRY_INFO;
import static org.folio.entitlement.utils.EntitlementServiceUtils.getErrorMessage;

import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.entity.FlowStageEntity;
import org.folio.entitlement.domain.entity.key.FlowStageKey;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.domain.model.RetryInformation;
import org.folio.entitlement.repository.FlowStageRepository;
import org.folio.flow.api.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
public abstract class DatabaseLoggingStage<C extends IdentifiableStageContext> implements Stage<C> {

  protected FlowStageRepository stageRepository;
  protected ThreadLocalModuleStageContext threadLocalModuleStageContext;

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

  @Autowired
  public void setThreadLocalModuleStageContext(ThreadLocalModuleStageContext threadLocalModuleStageContext) {
    this.threadLocalModuleStageContext = threadLocalModuleStageContext;
  }

  @Override
  public String getId() {
    return this.getClass().getSimpleName();
  }

  /**
   * Returns stage name based on {@link C} context object.
   *
   * @param context - stage context
   * @return stage name based on stage context
   */
  @SuppressWarnings("unused")
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

      log.error(format("Flow stage %s %s execution error", getId(), getStageName(context)), error);
    }

    var retryInfo = (RetryInformation) context.get(ATTR_RETRY_INFO);
    if (retryInfo != null) {
      stageExecutionEntity.setRetriesCount(retryInfo.getRetriesCount());
      stageExecutionEntity.setRetriesInfo(String.join("\n\n", retryInfo.getErrors()));
      context.put(ATTR_RETRY_INFO, null);
    }
    threadLocalModuleStageContext.clear();

    stageRepository.save(stageExecutionEntity);
  }
}
