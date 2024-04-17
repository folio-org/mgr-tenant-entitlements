package org.folio.entitlement.service.stage;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.integration.folio.ApplicationStageContext;
import org.folio.entitlement.service.EntitlementCrudService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class RevokeRequestDependencyValidator extends DatabaseLoggingStage<ApplicationStageContext> {

  private final EntitlementCrudService entitlementCrudService;

  @Override
  @Transactional
  public void execute(ApplicationStageContext context) {
    var applicationId = context.getApplicationId();
    var entitlementRequest = context.getEntitlementRequest();
    var tenantId = entitlementRequest.getTenantId();

    var installedDependentApplications = findInstalledDependentApplications(applicationId, tenantId);
    if (isNotEmpty(installedDependentApplications)) {
      throw new IllegalStateException(
        "The following applications must be uninstalled first: " + installedDependentApplications);
    }
  }

  private List<String> findInstalledDependentApplications(String applicationId, UUID tenantId) {
    var installedEntitlements = entitlementCrudService.findInstalledDependentEntitlements(applicationId, tenantId);

    return mapItems(installedEntitlements, Entitlement::getApplicationId);
  }
}
