package org.folio.entitlement.service.validator;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.folio.CommonStageContext;
import org.folio.entitlement.service.ApplicationDependencyValidatorService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(3)
@Component
@RequiredArgsConstructor
public class InterfaceIntegrityValidator extends DatabaseLoggingStage<CommonStageContext>
  implements EntitlementRequestValidator {

  private final ApplicationDependencyValidatorService dependencyValidatorService;

  @Override
  public void execute(CommonStageContext context) {
    var applicationDescriptors = context.getApplicationDescriptors();
    dependencyValidatorService.validateDescriptors(applicationDescriptors);
  }

  @Override
  public void validate(EntitlementRequest entitlementRequest) {
    dependencyValidatorService.validateApplications(entitlementRequest);
  }

  @Override
  public boolean shouldValidate(EntitlementRequest entitlementRequest) {
    return entitlementRequest.getType() == ENTITLE;
  }
}
