package org.folio.entitlement.service.stage;

import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.integration.folio.CommonStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.flow.ApplicationFlowService;

public abstract class FlowFinalizer extends AbstractFlowFinalizer<FlowEntity, CommonStageContext> {

  private final ApplicationFlowService applicationFlowService;

  /**
   * Injects beans from spring context.
   *
   * @param flowRepository - {@link ApplicationFlowRepository} bean
   */
  protected FlowFinalizer(FlowRepository flowRepository, ApplicationFlowService applicationFlowService) {
    super(flowRepository);
    this.applicationFlowService = applicationFlowService;
  }

  @Override
  protected void afterFlowStatusUpdate(CommonStageContext context) {
    applicationFlowService.removeAllQueuedFlows(context.getCurrentFlowId());
  }
}
