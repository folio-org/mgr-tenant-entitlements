package org.folio.entitlement.integration.keycloak;

import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.folio.entitlement.support.TestValues.module;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.am.model.Module;
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

  private static final String APP_ID_V1 = "app-foo-1.0.0";
  private static final String APP_ID_V2 = "app-foo-2.0.0";

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
    var applicationDesc = applicationDescriptor(APP_ID_V2,
      module("mod-foo", "2.0.0"), module("mod-bar", "1.0.0"), module("mod-baz", "1.0.0"));
    var entitledApplicationDesc = applicationDescriptor(APP_ID_V1,
      module("mod-foo", "1.0.0"), module("mod-bar", "1.0.0"), module("mod-test", "1.0.0"));

    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build();
    var flowParameters = flowParameters(request, applicationDesc, entitledApplicationDesc);
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    keycloakAuthResourceUpdater.execute(stageContext);

    verify(keycloakService).updateAuthResources(moduleDesc("mod-foo-1.0.0"), moduleDesc("mod-foo-2.0.0"), TENANT_NAME);
    verify(keycloakService).updateAuthResources(moduleDesc("mod-bar-1.0.0"), moduleDesc("mod-bar-1.0.0"), TENANT_NAME);
    verify(keycloakService).updateAuthResources(null, moduleDesc("mod-baz-1.0.0"), TENANT_NAME);
    verify(keycloakService).updateAuthResources(moduleDesc("mod-test-1.0.0"), null, TENANT_NAME);
  }

  @Test
  void execute_positive_updatedModule() {
    var applicationDesc = applicationDescriptor(APP_ID_V2, module("mod-foo", "2.0.0"));
    var entitledApplicationDesc = applicationDescriptor(APP_ID_V1, module("mod-foo", "1.0.0"));

    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build();
    var flowParameters = flowParameters(request, applicationDesc, entitledApplicationDesc);
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    keycloakAuthResourceUpdater.execute(stageContext);

    verify(keycloakService).updateAuthResources(moduleDesc("mod-foo-1.0.0"), moduleDesc("mod-foo-2.0.0"), TENANT_NAME);
  }

  @Test
  void execute_positive_deprecatedModule() {
    var applicationDesc = applicationDescriptor(APP_ID_V2);
    var entitledApplicationDesc = applicationDescriptor(APP_ID_V1, module("mod-foo", "1.0.0"));

    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build();
    var flowParameters = flowParameters(request, applicationDesc, entitledApplicationDesc);
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    keycloakAuthResourceUpdater.execute(stageContext);

    verify(keycloakService).updateAuthResources(moduleDesc("mod-foo-1.0.0"), null, TENANT_NAME);
  }

  private static ModuleDescriptor moduleDesc(String id) {
    return new ModuleDescriptor().id(id);
  }

  private static ApplicationDescriptor applicationDescriptor(String id, Module... modules) {
    return new ApplicationDescriptor().id(id)
      .modules(List.of(modules))
      .moduleDescriptors(mapItems(List.of(modules), module -> moduleDesc(module.getId())));
  }
}
