package org.folio.entitlement.integration.folio;

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

/**
 * Installs and uninstalls FOLIO modules for a tenant through their tenant API and keeps the module entitlements in
 * sync.
 *
 * <p>When a module accepts a tenant call as a background job, the operation waits for the job through
 * {@link TenantJobPoller}. The module entitlement is saved only after that wait succeeded and deleted only after the
 * module call succeeded, so a failed module is never recorded as installed.</p>
 */
@Log4j2
@RequiredArgsConstructor
public class FolioModuleService {

  private final FolioTenantApiClient folioTenantApiClient;
  private final EntitlementModuleService moduleService;
  private final TenantJobPoller tenantJobPoller;

  /**
   * Enables tenant for module using {@link ModuleRequest} object.
   *
   * <p>Deliberately not transactional: installation may wait for an asynchronous tenant job for a long time. The
   * module entitlement is saved only after the module call (and its job, if any) has succeeded.</p>
   *
   * @param moduleRequest - module request information holder
   */
  public void enable(ModuleRequest moduleRequest) {
    var key = EntitlementModuleKey.from(moduleRequest);
    applyIfInstalledOrElse(key, handleInstall(moduleRequest));
  }

  /**
   * Disables tenant for module using {@link ModuleRequest} object.
   *
   * <p>Deliberately not transactional: uninstallation may wait for an asynchronous tenant job. The entitlement is
   * deleted only after the module call has succeeded.</p>
   *
   * @param moduleRequest - module request information holder
   */
  public void disable(ModuleRequest moduleRequest) {
    var moduleKey = EntitlementModuleKey.from(moduleRequest);
    applyIfInstalledOrElse(moduleKey, handleRevoke(moduleRequest));
  }

  /**
   * Creates the action that enables a module depending on where it is already installed.
   *
   * <p>A module that is not installed at all is installed; a module installed for another application only gets its
   * entitlement saved; a module already installed for the current application is left as is.</p>
   *
   * @param moduleRequest - module request information holder
   * @return action receiving "installed for current application" and "installed for other applications" flags
   */
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

  /**
   * Creates the action that disables a module depending on where it is installed.
   *
   * <p>A module without an entitlement is skipped, so nothing is purged for a module whose installation failed. The
   * module is uninstalled only when no other application uses it, and the entitlement is deleted only after the
   * uninstallation (and its tenant job, if any) succeeded.</p>
   *
   * @param moduleRequest - module request information holder
   * @return action receiving "installed for current application" and "installed for other applications" flags
   */
  private BiConsumer<Boolean, Boolean> handleRevoke(ModuleRequest moduleRequest) {
    return (isInstalledForCurrentApp, isInstalledForOtherApps) -> {
      if (isNotInstalled(isInstalledForCurrentApp, isInstalledForOtherApps)) {
        log.info("Skipping module revoke as it is not installed: moduleId = {}, tenantId = {}",
          moduleRequest.getModuleId(), moduleRequest.getTenantId());
        return;
      }
      if (isInstalledForCurrentApp) {
        if (!isInstalledForOtherApps) {
          uninstallModule(moduleRequest);
        }
        moduleService.deleteModuleEntitlement(moduleRequest);
      }
    };
  }

  /**
   * Installs a module for a tenant: calls the module's tenant API, waits for its tenant job if the module accepted
   * the call as a background job, and only then saves the module entitlement.
   *
   * @param moduleRequest - module request information holder
   */
  public void installModule(ModuleRequest moduleRequest) {
    if (moduleRequest.getTenantInterface() == null) {
      log.debug("Skipping module installation, tenant interface is not found: moduleId = {}, tenantId = {}",
        moduleRequest.getModuleId(), moduleRequest.getTenantId());
      moduleService.save(moduleRequest);
      return;
    }

    log.info("Installing module: moduleId = {}, applicationId = {}, tenant = {}", moduleRequest.getModuleId(),
      moduleRequest.getApplicationId(), moduleRequest.getTenantName());
    var operation = folioTenantApiClient.install(moduleRequest);
    tenantJobPoller.await(moduleRequest, operation);
    moduleService.save(moduleRequest);
  }

  /**
   * Calls the module's tenant API to uninstall the module and waits for the resulting tenant job, if any.
   *
   * <p>Nothing is called for a module without a tenant interface, or for tenant API 2.0 when the request does not
   * purge.</p>
   *
   * @param moduleRequest - module request information holder
   */
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

    if (!moduleRequest.isPurge()) {
      log.debug("Ignoring module uninstallation stage, "
          + "since tenant API 2.0 does not require a call if purge==false: moduleId = {}, tenantId = {}",
        moduleRequest.getModuleId(), moduleRequest.getTenantId());
      return;
    }

    log.info("Uninstalling module: moduleId = {}, applicationId = {}, tenant = {}", moduleRequest.getModuleId(),
      moduleRequest.getApplicationId(), moduleRequest.getTenantName());
    var operation = folioTenantApiClient.uninstall(moduleRequest);
    tenantJobPoller.await(moduleRequest, operation);
  }

  /**
   * Uninstalls a module that uses the legacy tenant API: purges it with {@code DELETE /_/tenant}, or disables it with
   * {@code POST /_/tenant/disable} and waits for the resulting tenant job, if any.
   *
   * @param moduleRequest - module request information holder
   */
  private void uninstallLegacyModule(ModuleRequest moduleRequest) {
    if (TRUE.equals(moduleRequest.isPurge())) {
      folioTenantApiClient.uninstallLegacy(moduleRequest);
      return;
    }

    if (supportsDisable(moduleRequest.getTenantInterface())) {
      var operation = folioTenantApiClient.disableLegacy(moduleRequest);
      tenantJobPoller.await(moduleRequest, operation);
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
