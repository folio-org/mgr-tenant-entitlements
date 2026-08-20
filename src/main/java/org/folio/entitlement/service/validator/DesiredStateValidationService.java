package org.folio.entitlement.service.validator;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.stage.ApplicationStateTransitionPlanner;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DesiredStateValidationService {

  private final ApplicationStateTransitionPlanner transitionPlanner;
  private final DesiredStateApplicationFlowValidator applicationFlowValidator;
  private final DesiredStateWithUpgradeValidator withUpgradeValidator;
  private final DesiredStateWithRevokeValidator withRevokeValidator;

  public void validate(EntitlementRequest request) {
    var transitionPlan = transitionPlanner.plan(request);
    applicationFlowValidator.validate(request, transitionPlan);
    withUpgradeValidator.validate(request, transitionPlan);
    withRevokeValidator.validate(request, transitionPlan);
  }
}
