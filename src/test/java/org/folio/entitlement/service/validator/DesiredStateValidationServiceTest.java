package org.folio.entitlement.service.validator;

import static java.util.Collections.emptySet;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.entitlement.domain.dto.EntitlementRequestType;
import org.folio.entitlement.domain.model.ApplicationStateTransitionPlan;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.stage.ApplicationStateTransitionPlanner;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DesiredStateValidationServiceTest {

  @Mock private ApplicationStateTransitionPlanner transitionPlanner;
  @Mock private DesiredStateApplicationFlowValidator applicationFlowValidator;
  @Mock private DesiredStateWithUpgradeValidator withUpgradeValidator;
  @Mock private DesiredStateWithRevokeValidator withRevokeValidator;

  @Test
  void validate_positive_runsAllDesiredStateValidators() {
    var request = EntitlementRequest.builder()
      .tenantId(TENANT_ID)
      .applications(List.of())
      .type(EntitlementRequestType.STATE)
      .build();
    var transitionPlan = ApplicationStateTransitionPlan.of(emptySet(), emptySet(), emptySet());
    var service = new DesiredStateValidationService(
      transitionPlanner, applicationFlowValidator, withUpgradeValidator, withRevokeValidator);
    when(transitionPlanner.plan(request)).thenReturn(transitionPlan);
    InOrder inOrder = inOrder(applicationFlowValidator, withUpgradeValidator, withRevokeValidator);

    service.validate(request);

    inOrder.verify(applicationFlowValidator).validate(request, transitionPlan);
    inOrder.verify(withUpgradeValidator).validate(request, transitionPlan);
    inOrder.verify(withRevokeValidator).validate(request, transitionPlan);
  }
}
