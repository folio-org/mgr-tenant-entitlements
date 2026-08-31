package org.folio.entitlement.service.stage;

import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.FlowRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class FinishedFlowFinalizer extends AbstractFlowFinalizer<FlowEntity, CommonStageContext> {

  /**
   * Injects beans from spring context.
   *
   * @param flowRepository - {@link ApplicationFlowRepository} bean
   */
  public FinishedFlowFinalizer(FlowRepository flowRepository,
    @Qualifier("flowFinalizerStatusProvider") FlowFinalizerStatusProvider<CommonStageContext> statusProvider) {
    super(flowRepository, statusProvider);
  }
}
