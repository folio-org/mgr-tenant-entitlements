package org.folio.entitlement.domain.model;

import java.util.Map;
import java.util.UUID;
import lombok.ToString;
import org.apache.commons.collections4.MapUtils;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.flow.api.StageContext;

@ToString(callSuper = true)
public class ApplicationStageContext extends IdentifiableStageContext {

  public static final String PARAM_APPLICATION_ENTITLEMENT_TYPE = "applicationEntitlementType";
  public static final String PARAM_APPLICATION_FLOW_ID = "applicationFlowId";
  public static final String PARAM_APPLICATION_ID = "applicationId";
  public static final String PARAM_APPLICATION_DESCRIPTOR = "applicationDescriptor";
  public static final String PARAM_MODULE_DISCOVERY_DATA = "moduleDiscoveryData";
  public static final String PARAM_PLATFORM_NEWLY_ENTITLED = "platformNewlyEntitled";
  public static final String PARAM_PLATFORM_DEPENDENCIES_PREEXISTING = "platformDependenciesPreexisting";

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
   * Returns current application entitlement type.
   *
   * @return {@link EntitlementType} object
   */
  public EntitlementType getEntitlementType() {
    return context.getFlowParameter(PARAM_APPLICATION_ENTITLEMENT_TYPE);
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

  /**
   * Returns tenant id from {@link EntitlementRequest}.
   *
   * @return tenant identifier as {@link UUID}
   */
  public UUID getTenantId() {
    return context.<EntitlementRequest>getFlowParameter(CommonStageContext.PARAM_REQUEST).getTenantId();
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
   * Returns loaded module discovery data as map, where key is the module id, value - module location URL.
   *
   * <p>This value is loaded before installing/revoking application</p>
   *
   * @return module discovery table as {@link Map} object
   */
  public Map<String, String> getModuleDiscoveryData() {
    return context.get(PARAM_MODULE_DISCOVERY_DATA);
  }

  /**
   * Returns loaded module discovery data as map, where key is the module id, value - module location URL.
   *
   * <p>This value is loaded before installing/revoking application</p>
   *
   * @return module discovery table as {@link Map} object
   */
  public String getModuleDiscovery(String moduleId) {
    var moduleDiscoveryMap = context.<Map<String, String>>get(PARAM_MODULE_DISCOVERY_DATA);
    if (MapUtils.isEmpty(moduleDiscoveryMap)) {
      return null;
    }

    return moduleDiscoveryMap.get(moduleId);
  }

  /**
   * Sets loaded module discovery data as map, where key is the module id, value - module location URL.
   */
  public void withModuleDiscoveryData(Map<String, String> moduleLocationsMap) {
    context.put(PARAM_MODULE_DISCOVERY_DATA, moduleLocationsMap);
  }

  /**
   * Remembers whether this flow created the platform entitlement of the application, so a cancellation removes
   * only what the flow created.
   */
  public void withPlatformNewlyEntitled(boolean newlyEntitled) {
    context.put(PARAM_PLATFORM_NEWLY_ENTITLED, newlyEntitled);
  }

  public boolean isPlatformNewlyEntitled() {
    return Boolean.TRUE.equals(context.get(PARAM_PLATFORM_NEWLY_ENTITLED));
  }

  /**
   * Remembers that the platform application was entitled before the flow started, so its dependencies are kept on
   * cancel.
   *
   * @param preexisting - true if the dependencies of the application were already saved
   */
  public void withPlatformDependenciesPreexisting(boolean preexisting) {
    context.put(PARAM_PLATFORM_DEPENDENCIES_PREEXISTING, preexisting);
  }

  public boolean isPlatformDependenciesPreexisting() {
    return Boolean.TRUE.equals(context.get(PARAM_PLATFORM_DEPENDENCIES_PREEXISTING));
  }
}
