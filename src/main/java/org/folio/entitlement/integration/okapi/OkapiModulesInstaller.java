package org.folio.entitlement.integration.okapi;

import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor.ActionType.DISABLE;
import static org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor.ActionType.ENABLE;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.service.stage.StageContextUtils.getApplicationDescriptor;
import static org.folio.entitlement.service.stage.StageContextUtils.getEntitlementRequest;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.am.model.Module;
import org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor;
import org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor.ActionType;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.StageContext;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@RequiredArgsConstructor
public class OkapiModulesInstaller extends DatabaseLoggingStage {

  private final OkapiClient okapiClient;
  private final EntitlementModuleService moduleService;

  @Override
  public void execute(StageContext context) {
    var request = getEntitlementRequest(context);
    var action = request.getType() == ENTITLE ? ENABLE : DISABLE;
    var applicationDescriptor = getApplicationDescriptor(context);
    var descriptors = mapItems(applicationDescriptor.getModules(), module -> buildOkapiDescriptors(action, module));

    updateModuleEntitlements(request.getTenantId(), applicationDescriptor, action);

    var tenantName = context.<String>get(PARAM_TENANT_NAME);
    okapiClient.installTenantModules(tenantName, false, request.isPurge(), request.getTenantParameters(),
      request.isIgnoreErrors(), false, descriptors, request.getOkapiToken());
  }

  private void updateModuleEntitlements(UUID tenantId, ApplicationDescriptor appDescriptor, ActionType action) {
    var modules = appDescriptor.getModules();
    var applicationId = appDescriptor.getId();
    var moduleIds = mapItems(modules, Module::getId);

    if (ENABLE == action) {
      moduleService.saveAll(tenantId, applicationId, moduleIds);
    } else {
      moduleService.deleteAll(tenantId, applicationId, moduleIds);
    }
  }

  private static TenantModuleDescriptor buildOkapiDescriptors(ActionType action, Module module) {
    return new TenantModuleDescriptor().id(module.getId()).action(action);
  }
}
