package org.folio.entitlement.service.stage;

import static org.folio.entitlement.service.stage.StageContextUtils.getApplicationDescriptor;
import static org.folio.entitlement.service.stage.StageContextUtils.getEntitlementRequest;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.flow.api.StageContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationDescriptorValidator extends DatabaseLoggingStage {

  private final ApplicationManagerService applicationManagerService;

  @Override
  public void execute(StageContext context) {
    applicationManagerService.validate(getApplicationDescriptor(context),
      getEntitlementRequest(context).getOkapiToken());
  }
}
