package org.folio.entitlement.service;

import static org.apache.commons.lang3.exception.ExceptionUtils.getStackTrace;
import static org.folio.entitlement.integration.folio.FolioIntegrationUtils.parseTenantParameters;
import static org.folio.entitlement.integration.folio.model.ModuleRequest.getTenantInterfaceDescriptor;
import static org.folio.entitlement.utils.ParallelUtil.runParallel;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.dto.ReinstallResult;
import org.folio.entitlement.integration.am.model.ModuleDiscovery;
import org.folio.entitlement.integration.folio.FolioModuleService;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.integration.tm.TenantManagerService;
import org.folio.entitlement.integration.tm.model.Tenant;

@RequiredArgsConstructor
@Log4j2
public class ReinstallService {

  private final Supplier<ExecutorService> executorSupplier;
  private final FolioModuleService folioModuleService;
  private final TenantManagerService tenantManagerService;
  private final ApplicationManagerService applicationManagerService;

  public ReinstallResult reinstallApplications(String authToken, List<String> appIds, UUID tenantId,
    String tenantParameters) {

    var appDescriptors = applicationManagerService.getApplicationDescriptors(appIds, authToken);
    Stream<Callable<ReinstallResult>> appInstallTasks = appDescriptors.stream().map(
      applicationDescriptor -> () -> reinstallModules(authToken, applicationDescriptor.getId(),
        applicationDescriptor.getModuleDescriptors().stream().map(ModuleDescriptor::getId).toList(), tenantId,
        tenantParameters));

    var results = runParallel(executorSupplier, appInstallTasks.toList(), log::error);
    var reinstallResult = new ReinstallResult();
    reinstallResult.setEntitlements(
      results.stream().map(ReinstallResult::getEntitlements).flatMap(Collection::stream).filter(Objects::nonNull)
        .toList());
    reinstallResult.setErrors(
      results.stream().map(ReinstallResult::getErrors).flatMap(Collection::stream).filter(Objects::nonNull).toList());

    return reinstallResult;
  }

  public ReinstallResult reinstallModules(String authToken, String appId, List<String> moduleIds, UUID tenantId,
    String tenantParameters) {
    var reinstallResult = new ReinstallResult();
    var entitlements = new CopyOnWriteArrayList<String>();
    var errors = new CopyOnWriteArrayList<String>();
    reinstallResult.setEntitlements(entitlements);
    reinstallResult.setErrors(errors);

    var tenant = tenantManagerService.findTenant(tenantId, authToken);
    var moduleDiscoveries = applicationManagerService.getModuleDiscoveries(appId, authToken).getRecords().stream()
      .collect(Collectors.toMap(ModuleDiscovery::getId, v -> v));
    var moduleDescriptors =
      applicationManagerService.getApplicationDescriptor(appId, authToken).getModuleDescriptors().stream()
        .collect(Collectors.toMap(ModuleDescriptor::getId, v -> v));

    var entitleModuleRequests = moduleIds.stream().map(
      moduleId -> toModuleRequest(appId, moduleId, moduleDescriptors.get(moduleId), moduleDiscoveries.get(moduleId),
        tenant, tenantParameters)).toList();

    Stream<Callable<String>> installTasks =
      entitleModuleRequests.stream().map(req -> () -> performInstall(req, errors::add));

    runParallel(executorSupplier, installTasks.toList(), log::error).stream().filter(Objects::nonNull)
      .forEach(entitlements::add);
    return reinstallResult;
  }

  protected ModuleRequest toModuleRequest(String appId, String moduleId, ModuleDescriptor moduleDescriptor,
    ModuleDiscovery moduleDiscovery, Tenant tenant, String tenantParameters) {
    return ModuleRequest.builder().moduleId(moduleId).purge(false).applicationId(appId).tenantId(tenant.getId())
      .location(moduleDiscovery.getLocation()).tenantName(tenant.getName())
      .tenantInterface(getTenantInterfaceDescriptor(moduleDescriptor))
      .tenantParameters(parseTenantParameters(tenantParameters)).build();
  }

  protected String performInstall(ModuleRequest moduleRequest, Consumer<String> errorConsumer) {
    try {
      folioModuleService.installModule(moduleRequest);
      return moduleRequest.getModuleId();
    } catch (Exception e) {
      var error = "Error re-installing module " + moduleRequest.getModuleId();
      log.error(error, e);
      errorConsumer.accept(
        String.format("%s - %s %s.%n%s", error, e.getClass().getSimpleName(), e.getMessage(), getStackTrace(e)));
    }
    return null;
  }
}
