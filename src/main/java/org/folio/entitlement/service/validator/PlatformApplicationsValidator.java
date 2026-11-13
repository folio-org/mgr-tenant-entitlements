package org.folio.entitlement.service.validator;

import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.ApplicationScope;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

/**
 * Rejects the request if any requested application is not platform-scoped. Every violation is reported, and one
 * violation rejects the whole request.
 */
@Component
@RequiredArgsConstructor
public class PlatformApplicationsValidator extends DatabaseLoggingStage<CommonStageContext> {

  private final ApplicationManagerService applicationManagerService;

  @Override
  public void execute(CommonStageContext context) {
    var request = context.getEntitlementRequest();
    var descriptors = applicationManagerService.getApplicationDescriptors(
      request.getApplications(), request.getOkapiToken());
    var violations = descriptors.stream()
      .filter(descriptor -> descriptor.getScope() != ApplicationScope.PLATFORM)
      .map(descriptor -> new Parameter().key(descriptor.getId()).value("Application is not platform-scoped"))
      .toList();

    if (!violations.isEmpty()) {
      throw new RequestValidationException("Application is not platform-scoped", violations);
    }

    context.withApplicationDescriptors(descriptors);
  }
}
