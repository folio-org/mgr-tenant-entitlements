package org.folio.entitlement.service.stage;

import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.folio.common.utils.Collectors.toLinkedHashMap;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_DISCOVERY_DATA;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;

import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.am.model.Module;
import org.folio.entitlement.integration.am.model.ModuleDiscovery;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.flow.api.StageContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationDiscoveryLoader extends DatabaseLoggingStage {

  private final ApplicationManagerService applicationManagerService;

  @Override
  public void execute(StageContext context) {
    var entitlementRequest = context.<EntitlementRequest>getFlowParameter(PARAM_REQUEST);
    var applicationId = context.<String>getFlowParameter(PARAM_APP_ID);
    var applicationDescriptor = context.<ApplicationDescriptor>get(PARAM_APP_DESCRIPTOR);
    var token = entitlementRequest.getOkapiToken();

    var modules = applicationDescriptor.getModules();
    if (CollectionUtils.isEmpty(modules)) {
      return;
    }

    var moduleDiscoveries = applicationManagerService.getModuleDiscoveries(applicationId, token).getRecords();
    if (CollectionUtils.isEmpty(moduleDiscoveries)) {
      throw new IllegalStateException("Module discovery information is not found");
    }

    verifyDiscoveryInformationPerModule(context, moduleDiscoveries);
  }

  private static void verifyDiscoveryInformationPerModule(StageContext ctx, List<ModuleDiscovery> moduleDiscoveries) {
    var applicationDescriptor = ctx.<ApplicationDescriptor>get(PARAM_APP_DESCRIPTOR);
    var modules = applicationDescriptor.getModules();
    var moduleDiscoveryData = moduleDiscoveries.stream()
      .collect(toLinkedHashMap(ModuleDiscovery::getId, ModuleDiscovery::getLocation));

    var undefinedModules = modules.stream().map(Module::getId).collect(toCollection(LinkedHashSet::new));
    undefinedModules.removeAll(moduleDiscoveryData.keySet());

    if (isNotEmpty(undefinedModules)) {
      throw new IllegalStateException("Application discovery information is not defined for " + undefinedModules);
    }

    ctx.put(PARAM_MODULE_DISCOVERY_DATA, moduleDiscoveryData);
  }
}
