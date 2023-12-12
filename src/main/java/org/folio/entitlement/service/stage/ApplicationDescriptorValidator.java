package org.folio.entitlement.service.stage;

import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.flow.api.StageContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationDescriptorValidator extends DatabaseLoggingStage {

  private final ApplicationManagerService applicationManagerService;

  @Override
  public void execute(StageContext context) {
    var entitlementRequest = context.<EntitlementRequest>getFlowParameter(PARAM_REQUEST);
    var applicationDescriptor = context.<ApplicationDescriptor>get(PARAM_APP_DESCRIPTOR);
    applicationManagerService.validate(applicationDescriptor, entitlementRequest.getOkapiToken());
  }
}
