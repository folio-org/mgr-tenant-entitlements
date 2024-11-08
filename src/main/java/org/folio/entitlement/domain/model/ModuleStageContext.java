package org.folio.entitlement.domain.model;

import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_ID;

import java.util.UUID;
import lombok.ToString;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.flow.api.StageContext;

@ToString(callSuper = true)
public class ModuleStageContext extends IdentifiableStageContext {

  public static final String PARAM_MODULE_ID = "moduleId";
  public static final String PARAM_MODULE_NAME = "moduleName";
  public static final String PARAM_MODULE_TYPE = "moduleType";
  public static final String PARAM_MODULE_DISCOVERY = "moduleDiscovery";
  public static final String PARAM_MODULE_DESCRIPTOR = "moduleDescriptor";
  public static final String PARAM_INSTALLED_MODULE_DESCRIPTOR = "installedModuleDescriptor";
  public static final String ATTR_STAGE_NAME = "stageName";

  /**
   * Creates a {@link ModuleStageContext} wrapper from a {@link StageContext} object.
   *
   * @param stageContext - {@link StageContext} object
   */
  public ModuleStageContext(StageContext stageContext) {
    super(stageContext);
  }

  /**
   * Decorates {@link StageContext} with {@link ModuleStageContext} functionality.
   *
   * @param stageContext - {@link StageContext} object
   * @return created {@link ModuleStageContext} decorator object
   */
  public static ModuleStageContext decorate(StageContext stageContext) {
    return new ModuleStageContext(stageContext);
  }

  /**
   * Return application flow identifier.
   *
   * @return application flow identifier as {@link UUID} object
   */
  @Override
  public UUID getCurrentFlowId() {
    return context.getFlowParameter(PARAM_APPLICATION_FLOW_ID);
  }

  /**
   * Returns current application identifier.
   *
   * @return application identifier as {@link String}
   */
  public String getApplicationId() {
    return context.getFlowParameter(PARAM_APPLICATION_ID);
  }

  /**
   * Returns current application identifier.
   *
   * @return application identifier as {@link String}
   */
  public String getEntitledApplicationId() {
    return context.getFlowParameter(PARAM_ENTITLED_APPLICATION_ID);
  }

  /**
   * Returns entitlement request.
   *
   * @return {@link EntitlementRequest} object
   */
  public EntitlementRequest getEntitlementRequest() {
    return context.getFlowParameter(CommonStageContext.PARAM_REQUEST);
  }

  /**
   * Returns tenant id from {@link EntitlementRequest}.
   *
   * @return tenant identifier as {@link UUID}
   */
  public UUID getTenantId() {
    return getEntitlementRequest().getTenantId();
  }

  /**
   * Returns tenant name (should be loaded from mgr-tenants to be non-null).
   *
   * @return tenant name as {@link String}
   */
  public String getTenantName() {
    return context.get(CommonStageContext.PARAM_TENANT_NAME);
  }

  /**
   * Returns current module identifier from flow parameters.
   *
   * @return current module id as {@link String}
   */
  public String getModuleId() {
    return context.getFlowParameter(PARAM_MODULE_ID);
  }

  /**
   * Returns current module name from flow parameters.
   *
   * @return current module id as {@link String}
   */
  public String getModuleName() {
    return context.getFlowParameter(PARAM_MODULE_NAME);
  }

  /**
   * Returns module type from flow parameters.
   *
   * @return current module type as {@link ModuleType}
   */
  public ModuleType getModuleType() {
    return context.getFlowParameter(PARAM_MODULE_TYPE);
  }

  /**
   * Returns current module descriptor from flow parameters.
   *
   * @return current module descriptor as {@link ModuleDescriptor}
   */
  public ModuleDescriptor getModuleDescriptor() {
    return context.getFlowParameter(PARAM_MODULE_DESCRIPTOR);
  }

  /**
   * Returns installed module descriptor from flow parameters.
   *
   * <p>This value will be non-null only in module installer stages related to upgrade operation</p>
   *
   * @return current module descriptor as {@link ModuleDescriptor}
   */
  public ModuleDescriptor getInstalledModuleDescriptor() {
    return context.getFlowParameter(PARAM_INSTALLED_MODULE_DESCRIPTOR);
  }

  /**
   * Return module location URL from flow parameters.
   *
   * @return module location url as {@link String} object
   */
  public String getModuleDiscovery() {
    return context.getFlowParameter(PARAM_MODULE_DISCOVERY);
  }
}
