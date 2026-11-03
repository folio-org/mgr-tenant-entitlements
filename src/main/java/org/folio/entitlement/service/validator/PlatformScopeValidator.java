package org.folio.entitlement.service.validator;

import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.ApplicationScope;
import org.folio.entitlement.domain.model.PlatformStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.exception.RequestValidationException.Params;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

/**
 * Loads the application descriptor and rejects any application that is not platform-scoped.
 */
@Component
@RequiredArgsConstructor
public class PlatformScopeValidator extends DatabaseLoggingStage<PlatformStageContext> {

  private final ApplicationManagerService applicationManagerService;

  @Override
  public void execute(PlatformStageContext context) {
    var request = context.getPlatformRequest();
    var descriptor = applicationManagerService.getApplicationDescriptor(request.getApplicationId(),
      request.getOkapiToken());

    if (descriptor.getScope() != ApplicationScope.PLATFORM) {
      throw new RequestValidationException("Application is not platform-scoped",
        new Params().add("applicationId", request.getApplicationId()));
    }
  }
}
