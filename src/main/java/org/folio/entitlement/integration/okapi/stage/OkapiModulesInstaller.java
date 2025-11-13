package org.folio.entitlement.integration.okapi.stage;

import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor.ActionType.DISABLE;
import static org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor.ActionType.ENABLE;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.okapi.OkapiClient;
import org.folio.entitlement.integration.okapi.model.OkapiStageContext;
import org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor;
import org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor.ActionType;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@RequiredArgsConstructor
public class OkapiModulesInstaller extends DatabaseLoggingStage<OkapiStageContext> {

  private final OkapiClient okapiClient;
  private final EntitlementModuleService moduleService;

  @Override
  public void execute(OkapiStageContext context) {
    var request = context.getEntitlementRequest();
    var action = context.getEntitlementType() == ENTITLE ? ENABLE : DISABLE;
    var moduleDescriptors = context.getModuleDescriptors();
    updateModuleEntitlements(context, action, moduleDescriptors);

    var descriptors = mapItems(moduleDescriptors, desc -> buildTenantModuleDescriptor(action, desc));
    okapiClient.installTenantModules(context.getTenantName(), false, request.isPurge(), request.getTenantParameters(),
      request.isIgnoreErrors(), false, descriptors, request.getOkapiToken());
  }

  private void updateModuleEntitlements(OkapiStageContext ctx, ActionType action, List<ModuleDescriptor> descriptors) {
    var moduleIds = mapItems(descriptors, ModuleDescriptor::getId);
    var tenantId = ctx.getTenantId();
    var applicationId = ctx.getApplicationId();
    if (ENABLE == action) {
      moduleService.saveAll(tenantId, applicationId, moduleIds);
    } else {
      moduleService.deleteAll(tenantId, applicationId, moduleIds);
    }
  }

  private static TenantModuleDescriptor buildTenantModuleDescriptor(ActionType action, ModuleDescriptor descriptor) {
    return new TenantModuleDescriptor().id(descriptor.getId()).action(action);
  }
}
