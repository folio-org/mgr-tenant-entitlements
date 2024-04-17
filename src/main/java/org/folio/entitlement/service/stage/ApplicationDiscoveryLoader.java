package org.folio.entitlement.service.stage;

import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.common.utils.Collectors.toLinkedHashMap;

import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.am.model.Module;
import org.folio.entitlement.integration.am.model.ModuleDiscovery;
import org.folio.entitlement.integration.folio.ApplicationStageContext;
import org.folio.entitlement.service.ApplicationManagerService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationDiscoveryLoader extends DatabaseLoggingStage<ApplicationStageContext> {

  private final ApplicationManagerService applicationManagerService;

  @Override
  public void execute(ApplicationStageContext context) {
    var modules = context.getApplicationDescriptor().getModules();
    if (isEmpty(modules)) {
      return;
    }

    var entitlementRequest = context.getEntitlementRequest();
    var applicationId = context.getApplicationId();
    var token = entitlementRequest.getOkapiToken();

    var moduleDiscoveries = applicationManagerService.getModuleDiscoveries(applicationId, token).getRecords();
    if (isEmpty(moduleDiscoveries)) {
      throw new IllegalStateException("Module discovery information is not found for application: " + applicationId);
    }

    verifyDiscoveryInformation(context, moduleDiscoveries);
  }

  private static void verifyDiscoveryInformation(ApplicationStageContext ctx, List<ModuleDiscovery> locationMap) {
    var modules = ctx.getApplicationDescriptor().getModules();
    var moduleDiscoveryData = locationMap.stream()
      .collect(toLinkedHashMap(ModuleDiscovery::getId, ModuleDiscovery::getLocation));

    var undefinedModules = modules.stream().map(Module::getId).collect(toCollection(LinkedHashSet::new));
    undefinedModules.removeAll(moduleDiscoveryData.keySet());

    if (isNotEmpty(undefinedModules)) {
      throw new IllegalStateException("Application discovery information is not defined for " + undefinedModules);
    }

    ctx.withModuleDiscoveryData(moduleDiscoveryData);
  }
}
