package org.folio.entitlement.service.validator;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;

import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.ApplicationDependencyValidatorService;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(3)
@Component
@RequiredArgsConstructor
public class InterfaceIntegrityValidator implements EntitlementRequestValidator {

  private final ApplicationDependencyValidatorService dependencyValidatorService;

  @Override
  public void validate(EntitlementRequest request) {
    dependencyValidatorService.validateApplications(request.getTenantId(),
      new HashSet<>(request.getApplications()),
      request.getOkapiToken());
  }

  @Override
  public boolean shouldValidate(EntitlementRequest entitlementRequest) {
    return entitlementRequest.getType() == ENTITLE;
  }
}
