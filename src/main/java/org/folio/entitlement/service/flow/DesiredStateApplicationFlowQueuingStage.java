package org.folio.entitlement.service.flow;

import static java.util.stream.Collectors.toMap;
import static org.folio.entitlement.utils.EntitlementServiceUtils.toEntitlementType;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class DesiredStateApplicationFlowQueuingStage extends DatabaseLoggingStage<CommonStageContext> {

  private final ApplicationFlowService applicationFlowService;

  @Override
  @Transactional
  public void execute(CommonStageContext context) {
    var tenantId = context.getEntitlementRequest().getTenantId();
    var transitionPlan = context.getApplicationStateTransitionPlan();
    var currentFlowId = context.getCurrentFlowId();

    var applicationFlowsMap =  transitionPlan.nonEmptyBuckets()
      .flatMap(tb -> applicationFlowService.createQueuedApplicationFlows(
        currentFlowId, tb.getApplicationIds(),
        toEntitlementType(tb.getTransitionType()), tenantId).stream()
      )
      .collect(toMap(ApplicationFlow::getApplicationId, ApplicationFlow::getId));
    
    context.withQueuedApplicationFlows(applicationFlowsMap);
  }
}
