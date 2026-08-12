package org.folio.entitlement.integration.kong;

import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;
import org.folio.tools.kong.model.Service;
import org.folio.tools.kong.service.KongGatewayService;

@Log4j2
@RequiredArgsConstructor
public class KongModuleRouteUpdater extends ModuleDatabaseLoggingStage {

  private final KongGatewayService kongGatewayService;
  private final ApiGatewayConfigurationProperties properties;
  private final EntitlementModuleService entitlementModuleService;

  @Override
  public void execute(ModuleStageContext context) {
    if (context.getModuleType() == ModuleType.UI_MODULE) {
      return;
    }

    var moduleId = context.getModuleId();
    var location = context.getModuleDiscovery();

    if (location == null) {
      // deprecated module: removed from new app version, no discovery URL available
      if (isLastEntitledTenant(moduleId) && properties.getRouteManagement().isEnabled()) {
        deleteServiceAndRoutes(moduleId);
      }
      return;
    }

    updateRoutes(moduleId, location, context);
  }

  private void updateRoutes(String moduleId, String location, ModuleStageContext context) {
    if (properties.getRouteManagement().isEnabled()) {
      deleteServiceRoutesQuietly(moduleId);
    }

    kongGatewayService.upsertService(new Service().name(moduleId).url(location));
    log.debug("Upserted Kong service: moduleId = {}", moduleId);

    if (properties.getRouteManagement().isEnabled()) {
      kongGatewayService.addRoutes(List.of(context.getModuleDescriptor()));
      log.debug("Added Kong routes for module: moduleId = {}", moduleId);
    }

    if (properties.getTenantChecks().isEnabled()) {
      kongGatewayService.addTenantToModuleRoutes(moduleId, context.getTenantName());
      log.debug("Added tenant to Kong routes: moduleId = {}, tenant = {}", moduleId, context.getTenantName());
    }
  }

  private boolean isLastEntitledTenant(String moduleId) {
    return entitlementModuleService.getModuleEntitlements(moduleId, 2, 0).getTotalRecords() <= 1;
  }

  private void deleteServiceAndRoutes(String moduleId) {
    deleteServiceRoutesQuietly(moduleId);
    deleteServiceQuietly(moduleId);
    log.debug("Deleted Kong service and routes for deprecated module: moduleId = {}", moduleId);
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
}
