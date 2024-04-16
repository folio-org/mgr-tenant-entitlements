package org.folio.entitlement.integration.folio;

import java.util.Map;
import java.util.UUID;
import lombok.ToString;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.flow.api.StageContext;

@ToString(callSuper = true)
public class ApplicationStageContext extends IdentifiableStageContext {

  public static final String PARAM_APPLICATION_FLOW_ID = "applicationFlowId";
  public static final String PARAM_APPLICATION_ID = "applicationId";
  public static final String PARAM_APPLICATION_DESCRIPTOR = "applicationDescriptor";
  public static final String PARAM_MODULE_DISCOVERY_DATA = "moduleDiscoveryData";
  public static final String PARAM_MODULE_DISCOVERY = "moduleDiscovery";
  public static final String PARAM_MODULE_DESCRIPTOR = "moduleDescriptor";
  public static final String PARAM_MODULE_ID = "moduleId";

  public static final String PARAM_ENTITLED_APPLICATION_ID = "entitledApplicationId";
  public static final String PARAM_ENTITLED_APPLICATION_DESCRIPTOR = "entitledApplicationDescriptor";

  /**
   * Creates a {@link ApplicationStageContext} wrapper from a {@link StageContext} object.
   *
   * @param stageContext - {@link StageContext} object
   */
  public ApplicationStageContext(StageContext stageContext) {
    super(stageContext);
  }

  /**
   * Decorates {@link StageContext} with {@link ApplicationStageContext} functionality.
   *
   * @param stageContext - {@link StageContext} object
   * @return created {@link ApplicationStageContext} decorator object
   */
  public static ApplicationStageContext decorate(StageContext stageContext) {
    return new ApplicationStageContext(stageContext);
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
   * Returns entitlement request.
   *
   * @return {@link EntitlementRequest} object
   */
  public EntitlementRequest getEntitlementRequest() {
    return context.getFlowParameter(CommonStageContext.PARAM_REQUEST);
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
   * Provides request {@link ApplicationDescriptor} object.
   *
   * @return {@link ApplicationDescriptor} object
   */
  public ApplicationDescriptor getApplicationDescriptor() {
    return context.getFlowParameter(PARAM_APPLICATION_DESCRIPTOR);
  }

  /**
   * Returns entitled application identifier (for upgrade operation).
   *
   * @return entitled application identifier as {@link String}
   */
  public String getEntitledApplicationId() {
    return context.getFlowParameter(PARAM_ENTITLED_APPLICATION_ID);
  }

  /**
   * Provides previously entitled {@link ApplicationDescriptor} object for upgrade operation.
   *
   * @return entitled {@link ApplicationDescriptor} object
   */
  public ApplicationDescriptor getEntitledApplicationDescriptor() {
    return context.getFlowParameter(PARAM_ENTITLED_APPLICATION_DESCRIPTOR);
  }

  public void setTenantName(String tenantName) {
    context.put(CommonStageContext.PARAM_TENANT_NAME, tenantName);
  }

  public UUID getTenantId() {
    return context.<EntitlementRequest>getFlowParameter(CommonStageContext.PARAM_REQUEST).getTenantId();
  }

  public String getTenantName() {
    return context.get(CommonStageContext.PARAM_TENANT_NAME);
  }

  public String getModuleId() {
    return context.getFlowParameter(PARAM_MODULE_ID);
  }

  public ModuleDescriptor getModuleDescriptor() {
    return context.getFlowParameter(PARAM_MODULE_DESCRIPTOR);
  }

  public String getModuleDiscovery() {
    return context.getFlowParameter(PARAM_MODULE_DISCOVERY);
  }

  public Map<String, String> getModuleDiscoveries() {
    return context.get(PARAM_MODULE_DISCOVERY_DATA);
  }

  public void setModuleDiscoveryData(Map<String, String> moduleLocationsMap) {
    context.put(PARAM_MODULE_DISCOVERY_DATA, moduleLocationsMap);
  }
}
