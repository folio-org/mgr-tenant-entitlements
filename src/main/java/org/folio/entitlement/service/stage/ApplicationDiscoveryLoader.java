package org.folio.entitlement.service.stage;

import static java.util.stream.Collectors.toCollection;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.common.utils.Collectors.toLinkedHashMap;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.Module;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.integration.am.model.ModuleDiscovery;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.utils.EntitlementServiceUtils;
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

    var modulesWithoutProvides = getModuleIdsWithoutProvides(context);
    if (modules.stream().map(Module::getId).allMatch(modulesWithoutProvides::contains)) {
      context.withModuleDiscoveryData(Map.of());
      return;
    }

    var applicationId = context.getApplicationId();
    var token = context.getEntitlementRequest().getOkapiToken();

    var moduleDiscoveries = applicationManagerService.getModuleDiscoveries(applicationId, token).getRecords();
    if (isEmpty(moduleDiscoveries)) {
      throw new IllegalStateException("Module discovery information is not found for application: " + applicationId);
    }

    verifyDiscoveryInformation(context, moduleDiscoveries, modulesWithoutProvides);
  }

  private static Set<String> getModuleIdsWithoutProvides(ApplicationStageContext context) {
    return toStream(context.getApplicationDescriptor().getModuleDescriptors())
      .filter(EntitlementServiceUtils::hasNoProvides)
      .map(ModuleDescriptor::getId)
      .collect(toSet());
  }

  private static void verifyDiscoveryInformation(ApplicationStageContext ctx, List<ModuleDiscovery> locationMap,
    Set<String> modulesWithoutProvides) {
    var modules = ctx.getApplicationDescriptor().getModules();
    var moduleDiscoveryData = locationMap.stream()
      .collect(toLinkedHashMap(ModuleDiscovery::getId, ModuleDiscovery::getLocation));

    var undefinedModules = modules.stream().map(Module::getId).collect(toCollection(LinkedHashSet::new));
    undefinedModules.removeAll(moduleDiscoveryData.keySet());
    undefinedModules.removeAll(modulesWithoutProvides);

    if (isNotEmpty(undefinedModules)) {
      throw new IllegalStateException("Application discovery information is not defined for " + undefinedModules);
    }

    ctx.withModuleDiscoveryData(moduleDiscoveryData);
  }
}
