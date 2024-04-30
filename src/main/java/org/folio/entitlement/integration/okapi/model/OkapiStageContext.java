package org.folio.entitlement.integration.okapi.model;

import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_ID;

import java.util.List;
import java.util.UUID;
import lombok.ToString;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.domain.model.ModuleDescriptorHolder;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.flow.api.StageContext;

@ToString(callSuper = true)
public class OkapiStageContext extends IdentifiableStageContext {

  public static final String PARAM_APPLICATION_FLOW_ID = "applicationFlowId";
  public static final String PARAM_APPLICATION_ID = "applicationId";
  public static final String PARAM_MODULE_DESCRIPTORS = "moduleDescriptors";
  public static final String PARAM_MODULE_DESCRIPTOR_HOLDERS = "moduleDescriptorHolders";
  public static final String PARAM_UI_MODULE_DESCRIPTOR_HOLDERS = "uiModuleDescriptorHolders";
  public static final String PARAM_UI_MODULE_DESCRIPTORS = "uiModuleDescriptors";
  public static final String PARAM_DEPRECATED_MODULE_DESCRIPTORS = "deprecatedModuleDescriptors";
  public static final String PARAM_DEPRECATED_UI_MODULE_DESCRIPTORS = "deprecatedUiModuleDescriptors";

  /**
   * Creates a {@link OkapiStageContext} wrapper from a {@link StageContext} object.
   *
   * @param stageContext - {@link StageContext} object
   */
  public OkapiStageContext(StageContext stageContext) {
    super(stageContext);
  }

  /**
   * Decorates {@link StageContext} with {@link OkapiStageContext} functionality.
   *
   * @param stageContext - {@link StageContext} object
   * @return created {@link OkapiStageContext} decorator object
   */
  public static OkapiStageContext decorate(StageContext stageContext) {
    return new OkapiStageContext(stageContext);
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

  public List<ModuleDescriptor> getModuleDescriptors() {
    return context.getFlowParameter(PARAM_MODULE_DESCRIPTORS);
  }

  public List<ModuleDescriptor> getUiModuleDescriptors() {
    return context.getFlowParameter(PARAM_UI_MODULE_DESCRIPTORS);
  }

  public List<ModuleDescriptorHolder> getModuleDescriptorHolders() {
    return context.getFlowParameter(PARAM_MODULE_DESCRIPTOR_HOLDERS);
  }

  public List<ModuleDescriptorHolder> getUiModuleDescriptorHolders() {
    return context.getFlowParameter(PARAM_UI_MODULE_DESCRIPTOR_HOLDERS);
  }

  public List<ModuleDescriptor> getDeprecatedModuleDescriptors() {
    return context.getFlowParameter(PARAM_DEPRECATED_MODULE_DESCRIPTORS);
  }

  public List<ModuleDescriptor> getDeprecatedUiModuleDescriptors() {
    return context.getFlowParameter(PARAM_DEPRECATED_UI_MODULE_DESCRIPTORS);
  }
}
