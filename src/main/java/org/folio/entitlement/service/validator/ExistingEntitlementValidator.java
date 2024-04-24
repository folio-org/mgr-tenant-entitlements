package org.folio.entitlement.service.validator;

import static java.util.stream.Collectors.toSet;

import jakarta.persistence.EntityNotFoundException;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(2)
@Component
@RequiredArgsConstructor
public class ExistingEntitlementValidator extends DatabaseLoggingStage<CommonStageContext>
  implements EntitlementRequestValidator {

  private final EntitlementCrudService entitlementCrudService;

  @Override
  public void execute(CommonStageContext context) {
    validate(context.getEntitlementRequest());
  }

  @Override
  public void validate(EntitlementRequest entitlementRequest) {
    var entitlements = entitlementCrudService.getEntitlements(entitlementRequest);
    var applicationIds = entitlementRequest.getApplications();
    if (entitlements.size() != applicationIds.size()) {
      var notFoundEntitlements = new LinkedHashSet<>(applicationIds);
      notFoundEntitlements.removeAll(entitlements.stream().map(Entitlement::getApplicationId).collect(toSet()));
      throw new EntityNotFoundException("Entitlements are not found for applications: " + notFoundEntitlements);
    }
  }

  @Override
  public boolean shouldValidate(EntitlementRequest entitlementRequest) {
    return entitlementRequest.getType() == EntitlementType.REVOKE;
  }
}
