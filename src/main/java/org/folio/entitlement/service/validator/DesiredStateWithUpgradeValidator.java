package org.folio.entitlement.service.validator;

import static org.folio.common.utils.SemverUtils.getNames;
import static org.folio.entitlement.service.validator.ValidatorUtils.validateUpdatingApps;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DesiredStateWithUpgradeValidator extends DatabaseLoggingStage<CommonStageContext> {

  private final EntitlementCrudService entitlementService;

  @Override
  public void execute(CommonStageContext context) {
    var upgradeBucket = context.getApplicationStateTransitionPlan().upgradeBucket();
    if (upgradeBucket.isEmpty()) {
      return;
    }

    var tenantId = context.getEntitlementRequest().getTenantId();
    var applicationIds = upgradeBucket.getApplicationIds();

    var tenantEntitlements = loadEntitlementsByApplicationNames(tenantId, applicationIds);
    validateUpdatingApps(applicationIds, tenantEntitlements);
  }

  private List<Entitlement> loadEntitlementsByApplicationNames(UUID tenantId, Collection<String> applicationIds) {
    List<String> applicationNames;
    try {
      applicationNames = getNames(applicationIds);
    } catch (IllegalArgumentException e) {
      throw new RequestValidationException("Invalid applications provided for upgrade", "details", e.getMessage());
    }

    return entitlementService.findByApplicationNames(tenantId, applicationNames);
  }
}
