package org.folio.entitlement.integration.keycloak;

import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_DEPRECATED_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_MODULE_DESCRIPTOR_HOLDERS;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.moduleDescriptorHolder;
import static org.folio.entitlement.support.TestValues.okapiStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.token.TokenManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakAuthResourceUpdaterTest {

  @InjectMocks private KeycloakAuthResourceUpdater keycloakAuthResourceUpdater;
  @Mock private Keycloak keycloakClient;
  @Mock private TokenManager tokenManager;
  @Mock private KeycloakService keycloakService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_modulesChanged() {
    var modFooV1 = moduleDescriptor("mod-foo", "1.0.0");
    var modFooV2 = moduleDescriptor("mod-foo", "2.0.0");
    var modBar = moduleDescriptor("mod-bar", "1.0.0");
    var modBaz = moduleDescriptor("mod-baz", "1.0.0");
    var modTest = moduleDescriptor("mod-test", "1.0.0");

    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build();
    var moduleDescriptorHolders = List.of(
      moduleDescriptorHolder(modFooV2, modFooV1),
      moduleDescriptorHolder(modBar, modBar),
      moduleDescriptorHolder(modBaz, null));
    var flowParameters = Map.of(
      PARAM_REQUEST, request,
      PARAM_MODULE_DESCRIPTOR_HOLDERS, moduleDescriptorHolders,
      PARAM_DEPRECATED_MODULE_DESCRIPTORS, List.of(modTest));
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = okapiStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    keycloakAuthResourceUpdater.execute(stageContext);

    verify(keycloakService).updateAuthResources(modFooV1, modFooV2, TENANT_NAME);
    verify(keycloakService).updateAuthResources(null, modBaz, TENANT_NAME);
    verify(keycloakService).updateAuthResources(modTest, null, TENANT_NAME);
  }

  @Test
  void execute_positive_updatedModule() {
    var modFooV1 = moduleDescriptor("mod-foo", "1.0.0");
    var modFooV2 = moduleDescriptor("mod-foo", "2.0.0");

    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build();
    var moduleDescriptorHolders = List.of(moduleDescriptorHolder(modFooV2, modFooV1));
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_MODULE_DESCRIPTOR_HOLDERS, moduleDescriptorHolders);
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = okapiStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    keycloakAuthResourceUpdater.execute(stageContext);

    verify(keycloakService).updateAuthResources(modFooV1, modFooV2, TENANT_NAME);
  }

  @Test
  void execute_positive_deprecatedModule() {
    var modFooV1 = moduleDescriptor("mod-foo", "1.0.0");

    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build();
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_DEPRECATED_MODULE_DESCRIPTORS, List.of(modFooV1));
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = okapiStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    keycloakAuthResourceUpdater.execute(stageContext);

    verify(keycloakService).updateAuthResources(modFooV1, null, TENANT_NAME);
  }

  private static ModuleDescriptor moduleDescriptor(String name, String version) {
    return new ModuleDescriptor().id(name + "-" + version);
  }
}
