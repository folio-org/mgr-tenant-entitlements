package org.folio.entitlement.integration.apigw;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.entitlement.utils.EntitlementServiceUtils.hasNoProvides;
import static org.folio.entitlement.utils.EntitlementServiceUtils.isModuleUpdated;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.gateway.ApiGatewayService;
import org.folio.common.gateway.model.GatewayServiceDefinition;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;

@Log4j2
@RequiredArgsConstructor
public class ApiGatewayModuleRouteUpdater extends ModuleDatabaseLoggingStage {

  private final ApiGatewayService apiGatewayService;
  private final ApiGatewayConfigurationProperties properties;
  private final EntitlementModuleService entitlementModuleService;

  @Override
  public void execute(ModuleStageContext context) {
    if (context.getModuleType() == ModuleType.UI_MODULE) {
      return;
    }
    var moduleId = context.getModuleId();
    if (hasNoProvides(context.getModuleDescriptor())) {
      // new version has no routes: only the installed version's tenant routes and service must be retired
      removeTenantFromInstalledModuleRoutes(context);
      deleteUnusedInstalledModuleService(context);
      return;
    }
    var location = context.getModuleDiscovery();
    if (location == null) {
      deleteDeprecatedModuleService(context);
      return;
    }
    updateRoutes(moduleId, location, context);
    deleteUnusedInstalledModuleService(context);
  }

  private static boolean hasProvides(ModuleDescriptor moduleDescriptor) {
    return moduleDescriptor != null && isNotEmpty(moduleDescriptor.getProvides());
  }

  private void updateRoutes(String moduleId, String location, ModuleStageContext context) {
    var moduleDescriptor = context.getModuleDescriptor();
    var installedModuleDescriptor = context.getInstalledModuleDescriptor();
    apiGatewayService.upsertService(new GatewayServiceDefinition().name(moduleId).url(location));
    log.debug("Upserted API gateway service: moduleId = {}", moduleId);
    if (!isModuleUpdated(moduleDescriptor, installedModuleDescriptor)) {
      return;
    }
    removeTenantFromInstalledModuleRoutes(context);
    if (properties.getRouteManagement().isEnabled() && !entitlementModuleService.isEntitlementExist(moduleId)) {
      apiGatewayService.addRoutes(List.of(moduleDescriptor));
      log.debug("Added API gateway routes for module: moduleId = {}", moduleId);
    }
    if (properties.getTenantChecks().isEnabled()) {
      apiGatewayService.addTenantToModuleRoutes(moduleId, context.getTenantName());
      log.debug("Added tenant to API gateway routes: moduleId = {}, tenant = {}", moduleId, context.getTenantName());
    }
  }

  // deprecated module: removed from new app version, no discovery URL available
  private void deleteDeprecatedModuleService(ModuleStageContext context) {
    if (hasNoProvides(context.getInstalledModuleDescriptor())) {
      return;
    }
    var moduleId = context.getModuleId();
    if (entitlementModuleService.isNoOtherEntitlementExist(moduleId, context.getTenantId())
        && properties.getRouteManagement().isEnabled()) {
      deleteServiceAndRoutes(moduleId);
    }
  }

  private void removeTenantFromInstalledModuleRoutes(ModuleStageContext context) {
    var installedModuleDescriptor = context.getInstalledModuleDescriptor();
    if (properties.getTenantChecks().isEnabled() && hasProvides(installedModuleDescriptor)) {
      apiGatewayService.removeTenantFromModuleRoutes(installedModuleDescriptor.getId(), context.getTenantName());
      log.debug("Removed tenant from API gateway routes for installed module: moduleId = {}, tenant = {}",
        installedModuleDescriptor.getId(), context.getTenantName());
    }
  }

  private void deleteUnusedInstalledModuleService(ModuleStageContext context) {
    var installedModuleDescriptor = context.getInstalledModuleDescriptor();
    if (installedModuleDescriptor == null || hasNoProvides(installedModuleDescriptor)) {
      return;
    }
    var installedModuleId = installedModuleDescriptor.getId();
    if (installedModuleId.equals(context.getModuleId())) {
      return;
    }
    if (entitlementModuleService.isNoOtherEntitlementExist(installedModuleId, context.getTenantId())
        && properties.getRouteManagement().isEnabled()) {
      deleteServiceAndRoutes(installedModuleId);
    }
  }

  private void deleteServiceAndRoutes(String moduleId) {
    apiGatewayService.deleteServiceRoutes(moduleId);
    apiGatewayService.deleteService(moduleId);
    log.debug("Deleted API gateway service and routes for deprecated module: moduleId = {}", moduleId);
  }
}
