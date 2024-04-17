package org.folio.entitlement.integration.folio.model;

import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.integration.folio.FolioIntegrationUtils.parseTenantParameters;

import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.folio.ApplicationStageContext;
import org.folio.flow.api.StageContext;

@Data
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ModuleRequest {

  public static final String TENANT_INTERFACE_ID = "_tenant";

  private final String moduleId;
  private final String location;
  private final String applicationId;
  private final UUID tenantId;
  private final boolean purge;
  private final String tenantName;
  private final InterfaceDescriptor tenantInterface;
  private final List<Parameter> tenantParameters;

  /**
   * Creates {@link ModuleRequest} from {@link StageContext} object.
   *
   * @param context - {@link StageContext} object with flow and stage parameters
   * @return created unmodifiable {@link ModuleRequest} object
   */
  public static ModuleRequest fromStageContext(ApplicationStageContext context) {
    var request = context.getEntitlementRequest();
    return fromStageContext(context, request.isPurge());
  }

  /**
   * Creates {@link ModuleRequest} from {@link StageContext} object.
   *
   * @param context - {@link StageContext} object with flow and stage parameters
   * @return created unmodifiable {@link ModuleRequest} object
   */
  public static ModuleRequest fromStageContext(ApplicationStageContext context, boolean purge) {
    var request = context.getEntitlementRequest();

    return ModuleRequest.builder()
      .moduleId(context.getModuleId())
      .purge(purge)
      .applicationId(context.getApplicationId())
      .location(context.getModuleDiscovery())
      .tenantId(request.getTenantId())
      .tenantName(context.getTenantName())
      .tenantInterface(getTenantInterfaceDescriptor(context))
      .tenantParameters(parseTenantParameters(request.getTenantParameters()))
      .build();
  }

  private static InterfaceDescriptor getTenantInterfaceDescriptor(ApplicationStageContext context) {
    var moduleDescriptor = context.getModuleDescriptor();
    return toStream(moduleDescriptor.getProvides())
      .filter(interfaceDescriptor -> TENANT_INTERFACE_ID.equals(interfaceDescriptor.getId()))
      .findFirst()
      .orElse(null);
  }
}
