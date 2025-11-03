package org.folio.entitlement.service.validator;

import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.SemverUtils.getNames;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.folio.entitlement.service.validator.EntitlementRequestValidator.Order.UPGRADE_REQUEST;
import static org.folio.entitlement.service.validator.ValidatorUtils.validateUpdatingApps;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Order(UPGRADE_REQUEST)
@Component
@RequiredArgsConstructor
public class UpgradeRequestValidator extends DatabaseLoggingStage<CommonStageContext>
  implements EntitlementRequestValidator {

  private final EntitlementCrudService entitlementService;

  @Override
  public void execute(CommonStageContext context) {
    var request = context.getEntitlementRequest();
    var tenantEntitlements = loadEntitlementsByApplicationNames(request);
    validateUpdatingApps(request.getApplications(), tenantEntitlements);

    var entitledApplicationIds = mapItems(tenantEntitlements, Entitlement::getApplicationId);
    context.withEntitledApplicationIds(entitledApplicationIds);
  }

  @Override
  public void validate(EntitlementRequest request) {
    var tenantEntitlements = loadEntitlementsByApplicationNames(request);
    validateUpdatingApps(request.getApplications(), tenantEntitlements);
  }

  private List<Entitlement> loadEntitlementsByApplicationNames(EntitlementRequest request) {
    var tenantId = request.getTenantId();
    var applicationIds = request.getApplications();

    List<String> applicationNames;
    try {
      applicationNames = getNames(applicationIds);
    } catch (IllegalArgumentException e) {
      throw new RequestValidationException("Invalid applications provided for upgrade", "details", e.getMessage());
    }

    return entitlementService.findByApplicationNames(tenantId, applicationNames);
  }

  @Override
  public boolean shouldValidate(EntitlementRequest entitlementRequest) {
    return entitlementRequest.getType() == UPGRADE;
  }
}
