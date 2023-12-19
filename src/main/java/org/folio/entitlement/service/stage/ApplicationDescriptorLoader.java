package org.folio.entitlement.service.stage;

import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.stage.StageContextUtils.getApplicationId;
import static org.folio.entitlement.service.stage.StageContextUtils.getEntitlementRequest;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.flow.api.StageContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationDescriptorLoader extends DatabaseLoggingStage {

  private final ApplicationManagerService applicationManagerService;

  @Override
  public void execute(StageContext context) {
    var applicationId = getApplicationId(context);
    var entitlementRequest = getEntitlementRequest(context);
    var token = entitlementRequest.getOkapiToken();

    var descriptor = applicationManagerService.getApplicationDescriptor(applicationId, token);

    context.put(PARAM_APP_DESCRIPTOR, descriptor);
  }
}
