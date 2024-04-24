package org.folio.entitlement.integration.keycloak;

import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_INSTALLED_MODULE_DESCRIPTOR;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.moduleFlowParameters;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class KeycloakModuleResourceUpdaterTest {

  @InjectMocks private KeycloakModuleResourceUpdater keycloakModuleResourceUpdater;
  @Mock private Keycloak keycloakClient;
  @Mock private TokenManager tokenManager;
  @Mock private KeycloakService keycloakService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_newModule() {
    var moduleDescriptor = moduleDescriptor("mod-foo-1.0.0");

    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build();
    var flowParameters = moduleFlowParameters(request, moduleDescriptor);
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    keycloakModuleResourceUpdater.execute(stageContext);

    verify(keycloakService).updateAuthResources(null, moduleDescriptor, TENANT_NAME);
  }

  @Test
  void execute_positive_moduleUpdated() {
    var installedModuleDescriptor = moduleDescriptor("mod-foo-1.0.0");
    var moduleDescriptor = moduleDescriptor("mod-foo-2.0.0");

    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build();
    var flowParameters = moduleFlowParameters(request, moduleDescriptor, installedModuleDescriptor);
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    keycloakModuleResourceUpdater.execute(stageContext);

    verify(keycloakService).updateAuthResources(installedModuleDescriptor, moduleDescriptor, TENANT_NAME);
  }

  @Test
  void execute_positive_deprecatedModule() {
    var installedModuleDescriptor = moduleDescriptor("mod-foo-1.0.0");

    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build();
    var flowParameters = Map.of(
      PARAM_REQUEST, request,
      PARAM_INSTALLED_MODULE_DESCRIPTOR, installedModuleDescriptor,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    keycloakModuleResourceUpdater.execute(stageContext);

    verify(keycloakService).updateAuthResources(installedModuleDescriptor, null, TENANT_NAME);
  }

  private static ModuleDescriptor moduleDescriptor(String id) {
    return new ModuleDescriptor().id(id);
  }
}
