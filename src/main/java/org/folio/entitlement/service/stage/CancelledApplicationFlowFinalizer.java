package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.ExecutionStatus.CANCELLED;

import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.springframework.stereotype.Component;

@Component
public class CancelledApplicationFlowFinalizer
  extends AbstractFlowFinalizer<ApplicationFlowEntity, ApplicationStageContext> {

  /**
   * Injects beans from spring context.
   *
   * @param applicationFlowRepository - {@link ApplicationFlowRepository} bean
   */
  public CancelledApplicationFlowFinalizer(ApplicationFlowRepository applicationFlowRepository) {
    super(applicationFlowRepository);
  }

  @Override
  protected ExecutionStatus getFinalStatus() {
    return CANCELLED;
  }
}
