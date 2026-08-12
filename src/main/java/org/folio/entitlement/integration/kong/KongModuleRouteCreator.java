package org.folio.entitlement.integration.kong;

import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;
import org.folio.tools.kong.model.Service;
import org.folio.tools.kong.service.KongGatewayService;

@Log4j2
@RequiredArgsConstructor
public class KongModuleRouteCreator extends ModuleDatabaseLoggingStage {

  private final KongGatewayService kongGatewayService;
  private final ApiGatewayConfigurationProperties properties;

  @Override
  public void execute(ModuleStageContext context) {
    if (context.getModuleType() == ModuleType.UI_MODULE) {
      return;
    }

    var moduleId = context.getModuleId();
    var location = context.getModuleDiscovery();

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

  @Override
  public void cancel(ModuleStageContext context) {
    if (context.getModuleType() == ModuleType.UI_MODULE) {
      return;
    }

    var moduleId = context.getModuleId();
    if (properties.getRouteManagement().isEnabled()) {
      deleteServiceRoutesQuietly(moduleId);
    }
    deleteServiceQuietly(moduleId);
  }

  @Override
  public boolean shouldCancelIfFailed(ModuleStageContext context) {
    return true;
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
