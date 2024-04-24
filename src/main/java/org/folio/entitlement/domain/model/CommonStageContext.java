package org.folio.entitlement.domain.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.ToString;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.service.stage.ApplicationDescriptorTreeLoader;
import org.folio.flow.api.StageContext;

@ToString(callSuper = true)
public class CommonStageContext extends IdentifiableStageContext {

  public static final String PARAM_REQUEST = "entitlementRequest";
  public static final String PARAM_APP_DESCRIPTORS = "applicationDescriptors";
  public static final String PARAM_QUEUED_APPLICATION_FLOWS = "queuedApplicationFlows";
  public static final String PARAM_ENTITLED_APPLICATION_IDS = "entitledApplicationIds";
  public static final String PARAM_TENANT_NAME = "tenantName";

  /**
   * Creates {@link CommonStageContext} from {@link StageContext}.
   *
   * @param stageContext - {@link StageContext} object
   */
  public CommonStageContext(StageContext stageContext) {
    super(stageContext);
  }

  /**
   * Returns entitlement request from {@link StageContext} object.
   */
  public EntitlementRequest getEntitlementRequest() {
    return context.getFlowParameter(PARAM_REQUEST);
  }

  /**
   * Decorates {@link StageContext} with {@link CommonStageContext} functionality.
   *
   * @param stageContext - {@link StageContext} object
   * @return created {@link CommonStageContext} decorator object
   */
  public static CommonStageContext decorate(StageContext stageContext) {
    return new CommonStageContext(stageContext);
  }

  /**
   * Returns all application descriptors in request and all dependent application descriptors.
   *
   * @return list with loaded application descriptors
   */
  public List<ApplicationDescriptor> getApplicationDescriptors() {
    return context.get(PARAM_APP_DESCRIPTORS);
  }

  /**
   * Returns all entitled application descriptors for upgrade request.
   *
   * @return list with loaded entitled application descriptors
   */
  public List<ApplicationDescriptor> getEntitledApplicationDescriptors() {
    return context.get(PARAM_ENTITLED_APPLICATION_IDS);
  }

  /**
   * Returns a map where key is application id, and value is the corresponding UUID for application flow with QUEUED
   * status.
   *
   * @return application-id to queued application flow id map
   */
  public Map<String, UUID> getQueuedApplicationFlows() {
    return context.get(PARAM_QUEUED_APPLICATION_FLOWS);
  }

  /**
   * Returns entitled application ids (after validation) for upgrade request.
   *
   * @return {@link List} with entitled application ids as {@link String}
   */
  public List<String> getEntitledApplicationIds() {
    return context.get(PARAM_ENTITLED_APPLICATION_IDS);
  }

  /**
   * Adds loaded tenant name to {@link StageContext}.
   *
   * @param tenantName - loaded tenant name from {@code mgr-tenants}
   */
  public void withTenantName(String tenantName) {
    context.put(PARAM_TENANT_NAME, tenantName);
  }

  /**
   * Sets all application descriptors in request and, optionally, all dependent application descriptors.
   *
   * <p>Application descriptors are loaded
   * in {@link ApplicationDescriptorTreeLoader} stage</p>
   */
  public void withApplicationDescriptors(List<ApplicationDescriptor> applicationDescriptors) {
    context.put(PARAM_APP_DESCRIPTORS, applicationDescriptors);
  }

  /**
   * Sets all application descriptors in request and all dependent application descriptors.
   *
   * <p>Application descriptors are loaded
   * in {@link ApplicationDescriptorTreeLoader} stage</p>
   */
  public void withEntitledApplicationDescriptors(List<ApplicationDescriptor> applicationDescriptors) {
    context.put(PARAM_ENTITLED_APPLICATION_IDS, applicationDescriptors);
  }

  /**
   * Sets queued application flow ids per application id as {@link Map}.
   *
   * @param queuedApplicationFlows - queued application flow ids map
   */
  public void withQueuedApplicationFlows(Map<String, UUID> queuedApplicationFlows) {
    context.put(PARAM_QUEUED_APPLICATION_FLOWS, queuedApplicationFlows);
  }

  /**
   * Sets entitled application ids for upgrade flow.
   *
   * @param entitledApplicationIds - queued application flow ids map
   */
  public void withEntitledApplicationIds(List<String> entitledApplicationIds) {
    context.put(PARAM_ENTITLED_APPLICATION_IDS, entitledApplicationIds);
  }

  /**
   * Clears context from application descriptors after preparation of dedicated flow for each application.
   */
  public void clearContext() {
    context.remove(PARAM_APP_DESCRIPTORS);
    context.remove(PARAM_QUEUED_APPLICATION_FLOWS);
    context.remove(PARAM_ENTITLED_APPLICATION_IDS);
  }
}
