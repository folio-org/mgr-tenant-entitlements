package org.folio.entitlement.integration.folio.model;

import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.integration.folio.FolioIntegrationUtils.parseTenantParameters;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_DISCOVERY;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;

import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.flow.api.StageContext;
import org.folio.security.domain.model.descriptor.InterfaceDescriptor;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;

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
  public static ModuleRequest fromStageContext(StageContext context) {
    var request = context.<EntitlementRequest>getFlowParameter(PARAM_REQUEST);
    return fromStageContext(context, request.isPurge());
  }

  /**
   * Creates {@link ModuleRequest} from {@link StageContext} object.
   *
   * @param context - {@link StageContext} object with flow and stage parameters
   * @return created unmodifiable {@link ModuleRequest} object
   */
  public static ModuleRequest fromStageContext(StageContext context, boolean purge) {
    var request = context.<EntitlementRequest>getFlowParameter(PARAM_REQUEST);

    return ModuleRequest.builder()
      .moduleId(context.getFlowParameter(PARAM_MODULE_ID))
      .purge(purge)
      .applicationId(context.getFlowParameter(PARAM_APP_ID))
      .location(context.getFlowParameter(PARAM_MODULE_DISCOVERY))
      .tenantId(request.getTenantId())
      .tenantName(context.get(PARAM_TENANT_NAME))
      .tenantInterface(getTenantInterfaceDescriptor(context))
      .tenantParameters(parseTenantParameters(request.getTenantParameters()))
      .build();
  }

  private static InterfaceDescriptor getTenantInterfaceDescriptor(StageContext context) {
    var moduleDescriptor = context.<ModuleDescriptor>getFlowParameter(PARAM_MODULE_DESCRIPTOR);
    return toStream(moduleDescriptor.getProvides())
      .filter(interfaceDescriptor -> TENANT_INTERFACE_ID.equals(interfaceDescriptor.getId()))
      .findFirst()
      .orElse(null);
  }
}
