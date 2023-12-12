package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.ApplicationDependencyService;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.flow.api.StageContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationDescriptorLoader extends DatabaseLoggingStage {

  private final ApplicationManagerService applicationManagerService;
  private final ApplicationDependencyService applicationDependencyService;

  @Override
  public void execute(StageContext context) {
    var applicationId = context.<String>getFlowParameter(PARAM_APP_ID);
    var entitlementRequest = context.<EntitlementRequest>getFlowParameter(PARAM_REQUEST);
    var tenantId = entitlementRequest.getTenantId();
    var token = entitlementRequest.getOkapiToken();
    var descriptor = applicationManagerService.getApplicationDescriptor(applicationId, token);
    if (entitlementRequest.getType() == ENTITLE) {
      applicationDependencyService.saveEntitlementDependencies(tenantId, applicationId, descriptor.getDependencies());
    }

    context.put(PARAM_APP_DESCRIPTOR, descriptor);
  }
}
