package org.folio.entitlement.integration.kong;

import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;
import org.folio.tools.kong.service.KongGatewayService;

@Log4j2
@RequiredArgsConstructor
public class KongModuleRouteCleaner extends ModuleDatabaseLoggingStage {

  private final KongGatewayService kongGatewayService;
  private final ApiGatewayConfigurationProperties properties;
  private final EntitlementModuleService entitlementModuleService;

  @Override
  public void execute(ModuleStageContext context) {
    if (context.getModuleType() == ModuleType.UI_MODULE) {
      return;
    }

    var moduleId = context.getModuleId();

    if (properties.getTenantChecks().isEnabled()) {
      removeTenantFromModuleRoutesQuietly(moduleId, context.getTenantName());
      return;
    }

    // wildcard mode: delete service and routes only when this is the last entitled tenant
    if (isLastEntitledTenant(moduleId) && properties.getRouteManagement().isEnabled()) {
      deleteServiceAndRoutes(moduleId);
    }
  }

  private boolean isLastEntitledTenant(String moduleId) {
    return entitlementModuleService.getModuleEntitlements(moduleId, 2, 0).getTotalRecords() <= 1;
  }

  private void deleteServiceAndRoutes(String moduleId) {
    deleteServiceRoutesQuietly(moduleId);
    deleteServiceQuietly(moduleId);
    log.debug("Deleted Kong service and routes for last-entitled module: moduleId = {}", moduleId);
  }

  private void deleteServiceRoutesQuietly(String moduleId) {
    try {
      kongGatewayService.deleteServiceRoutes(moduleId);
    } catch (NoSuchElementException e) {
      log.debug("Kong service not found when deleting routes, skipping: moduleId = {}", moduleId);
    }
  }

  private void deleteServiceQuietly(String moduleId) {
    try {
      kongGatewayService.deleteService(moduleId);
    } catch (Exception e) {
      log.debug("Failed to delete Kong service, skipping: moduleId = {}", moduleId);
    }
  }

  private void removeTenantFromModuleRoutesQuietly(String moduleId, String tenantName) {
    try {
      kongGatewayService.removeTenantFromModuleRoutes(moduleId, tenantName);
      log.debug("Removed tenant from Kong routes: moduleId = {}, tenant = {}", moduleId, tenantName);
    } catch (Exception e) {
      log.error("Failed to remove tenant from Kong routes, skipping: moduleId = {}, tenant = {}", moduleId, tenantName);
    }
  }
}
