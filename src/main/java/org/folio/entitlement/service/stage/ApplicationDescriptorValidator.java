package org.folio.entitlement.service.stage;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.ApplicationManagerService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationDescriptorValidator extends DatabaseLoggingStage<ApplicationStageContext> {

  private final ApplicationManagerService applicationManagerService;

  @Override
  public void execute(ApplicationStageContext ctx) {
    applicationManagerService.validate(ctx.getApplicationDescriptor(), ctx.getEntitlementRequest().getOkapiToken());
  }
}
