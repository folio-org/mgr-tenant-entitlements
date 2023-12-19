package org.folio.entitlement.service.stage;

import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.flow.api.StageContext;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StageContextUtils {

  public static EntitlementRequest getEntitlementRequest(StageContext context) {
    return context.getFlowParameter(PARAM_REQUEST);
  }

  public static String getApplicationId(StageContext context) {
    return context.getFlowParameter(PARAM_APP_ID);
  }

  public static ApplicationDescriptor getApplicationDescriptor(StageContext context) {
    return context.get(PARAM_APP_DESCRIPTOR);
  }
}
