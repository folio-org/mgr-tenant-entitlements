package org.folio.entitlement.service.flow;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EntitlementFlowConstants {

  public static final String PARAM_APP_ID = "applicationId";
  public static final String PARAM_APP_DESCRIPTOR = "applicationDescriptor";
  public static final String PARAM_REQUEST = "entitlementRequest";
  public static final String PARAM_TENANT_NAME = "tenantName";

  public static final String PARAM_MODULE_DISCOVERY_DATA = "moduleDiscoveryData";
  public static final String PARAM_MODULE_DISCOVERY = "moduleDiscovery";
  public static final String PARAM_MODULE_DESCRIPTOR = "moduleDescriptor";
  public static final String PARAM_MODULE_ID = "moduleId";
}
