package org.folio.entitlement.integration.folio;

import static java.lang.Boolean.FALSE;
import static java.lang.Boolean.TRUE;
import static org.folio.entitlement.utils.TenantApiUtils.isLegacyApi;
import static org.folio.entitlement.utils.TenantApiUtils.supportsDisable;

import java.util.List;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.entity.key.EntitlementModuleKey;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.service.EntitlementModuleService;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@RequiredArgsConstructor
public class FolioModuleService {

  private final FolioTenantApiClient folioTenantApiClient;
  private final EntitlementModuleService moduleService;

  /**
   * Enables tenant for module using {@link ModuleRequest} object.
   *
   * @param moduleRequest - module request information holder
   */
  @Transactional
  public void enable(ModuleRequest moduleRequest) {
    var key = EntitlementModuleKey.from(moduleRequest);
    applyIfInstalledOrElse(key, handleInstall(moduleRequest));
  }

  /**
   * Disables tenant for module using {@link ModuleRequest} object.
   *
   * @param moduleRequest - module request information holder
   */
  @Transactional
  public void disable(ModuleRequest moduleRequest) {
    var moduleKey = EntitlementModuleKey.from(moduleRequest);
    applyIfInstalledOrElse(moduleKey, handleRevoke(moduleRequest));
  }

  private BiConsumer<Boolean, Boolean> handleInstall(ModuleRequest moduleRequest) {
    return (isInstalledForCurrentApp, isInstalledForOtherApps) -> {
      if (isNotInstalled(isInstalledForCurrentApp, isInstalledForOtherApps)) {
        installModule(moduleRequest);
        return;
      }
      log.info("A module is already installed [moduleId: {}, tenantId: {}]", moduleRequest.getModuleId(),
        moduleRequest.getTenantId());
      if (!isInstalledForCurrentApp) {
        moduleService.save(moduleRequest);
      }
    };
  }

  private BiConsumer<Boolean, Boolean> handleRevoke(ModuleRequest moduleRequest) {
    return (isInstalledForCurrentApp, isInstalledForOtherApps) -> {
      if (isNotInstalled(isInstalledForCurrentApp, isInstalledForOtherApps)) {
        log.info("Skipping module revoke as it is not installed: moduleId = {}, tenantId = {}",
          moduleRequest.getModuleId(), moduleRequest.getTenantId());
        return;
      }
      if (isInstalledForCurrentApp) {
        moduleService.deleteModuleEntitlement(moduleRequest);
        if (!isInstalledForOtherApps) {
          uninstallModule(moduleRequest);
        }
      }
    };
  }

  public void installModule(ModuleRequest moduleRequest) {
    moduleService.save(moduleRequest);
    if (moduleRequest.getTenantInterface() == null) {
      log.debug("Skipping module installation, tenant interface is not found: moduleId = {}, tenantId = {}",
        moduleRequest.getModuleId(), moduleRequest.getTenantId());
      return;
    }

    folioTenantApiClient.install(moduleRequest);
  }

  private void uninstallModule(ModuleRequest moduleRequest) {
    if (moduleRequest.getTenantInterface() == null) {
      log.debug("Skipping module uninstallation, tenant interface is not found: moduleId = {}, tenantId = {}",
        moduleRequest.getModuleId(), moduleRequest.getTenantId());
      return;
    }

    if (isLegacyApi(moduleRequest.getTenantInterface())) {
      uninstallLegacyModule(moduleRequest);
      return;
    }

    if (FALSE.equals(moduleRequest.isPurge())) {
      log.debug("Ignoring module uninstallation stage, "
          + "since tenant API 2.0 does not require a call if purge==false: moduleId = {}, tenantId = {}",
        moduleRequest.getModuleId(), moduleRequest.getTenantId());
      return;
    }

    folioTenantApiClient.uninstall(moduleRequest);
  }

  private void uninstallLegacyModule(ModuleRequest moduleRequest) {
    if (TRUE.equals(moduleRequest.isPurge())) {
      folioTenantApiClient.uninstallLegacy(moduleRequest);
      return;
    }

    if (supportsDisable(moduleRequest.getTenantInterface())) {
      folioTenantApiClient.disableLegacy(moduleRequest);
      return;
    }

    log.warn("Disable operation is not supported by tenant API of module: {}", moduleRequest.getModuleId());
  }

  private void applyIfInstalledOrElse(EntitlementModuleKey key, BiConsumer<Boolean, Boolean> installationConsumer) {
    var moduleId = key.getModuleId();
    var tenantId = key.getTenantId();
    var applicationId = key.getApplicationId();
    var entitlements = moduleService.findAllModuleEntitlements(moduleId, tenantId);
    var isInstalledForCurrentApp = existsByAppId(entitlements, applicationId);
    var isInstalledForOtherApps = isInstalledForCurrentApp ? entitlements.size() > 1 : !entitlements.isEmpty();

    installationConsumer.accept(isInstalledForCurrentApp, isInstalledForOtherApps);
  }

  protected static boolean existsByAppId(List<Entitlement> entitlements, String applicationId) {
    return entitlements.stream().anyMatch(e -> e.getApplicationId().equals(applicationId));
  }

  protected static boolean isNotInstalled(Boolean isInstalledForCurrentApp, Boolean isInstalledForOtherApps) {
    return !(isInstalledForOtherApps || isInstalledForCurrentApp);
  }
}
