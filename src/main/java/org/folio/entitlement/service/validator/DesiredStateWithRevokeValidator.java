package org.folio.entitlement.service.validator;

import static java.util.stream.Collectors.toSet;

import jakarta.persistence.EntityNotFoundException;
import java.util.LinkedHashSet;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DesiredStateWithRevokeValidator extends DatabaseLoggingStage<CommonStageContext> {

  private final EntitlementCrudService entitlementCrudService;

  @Override
  public void execute(CommonStageContext context) {
    var revokeBucket = context.getApplicationStateTransitionPlan().revokeBucket();
    if (revokeBucket.isEmpty()) {
      return;
    }

    var tenantId = context.getEntitlementRequest().getTenantId();
    var applicationIds = revokeBucket.getApplicationIds();

    var entitlements = entitlementCrudService.findByApplicationIds(tenantId, applicationIds);
    if (entitlements.size() != applicationIds.size()) {
      var notFoundEntitlements = new LinkedHashSet<>(applicationIds);
      notFoundEntitlements.removeAll(entitlements.stream().map(Entitlement::getApplicationId).collect(toSet()));
      throw new EntityNotFoundException("Entitlements are not found for applications: " + notFoundEntitlements);
    }
  }
}
