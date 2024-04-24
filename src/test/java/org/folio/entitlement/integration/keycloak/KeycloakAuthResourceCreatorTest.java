package org.folio.entitlement.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.folio.entitlement.support.TestValues.simpleApplicationDescriptor;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
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
class KeycloakAuthResourceCreatorTest {

  @InjectMocks private KeycloakAuthResourceCreator keycloakAuthResourceCreator;
  @Mock private Keycloak keycloakClient;
  @Mock private TokenManager tokenManager;
  @Mock private KeycloakService keycloakService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    var applicationDescriptor = simpleApplicationDescriptor(APPLICATION_ID);
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = flowParameters(entitlementRequest(true), applicationDescriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    keycloakAuthResourceCreator.execute(stageContext);

    verify(keycloakClient).tokenManager();
    var moduleDescriptor = applicationDescriptor.getModuleDescriptors().get(0);
    verify(keycloakService).updateAuthResources(null, moduleDescriptor, TENANT_NAME);
  }

  @Test
  void cancel_positive() {
    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    var applicationDescriptor = simpleApplicationDescriptor(APPLICATION_ID);
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = flowParameters(entitlementRequest(true), applicationDescriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    keycloakAuthResourceCreator.cancel(stageContext);

    verify(keycloakClient).tokenManager();
    var moduleDescriptor = applicationDescriptor.getModuleDescriptors().get(0);
    verify(keycloakService).removeAuthResources(moduleDescriptor, TENANT_NAME);
  }

  @Test
  void cancel_positive_purgeOnRollbackFalse() {
    var applicationDescriptor = simpleApplicationDescriptor(APPLICATION_ID);
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = flowParameters(entitlementRequest(false), applicationDescriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    keycloakAuthResourceCreator.cancel(stageContext);

    verifyNoInteractions(keycloakClient, keycloakService);
  }

  @Test
  void shouldCancelIfFailed_positive() {
    var applicationDescriptor = simpleApplicationDescriptor(APPLICATION_ID);
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = flowParameters(entitlementRequest(false), applicationDescriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var result = keycloakAuthResourceCreator.shouldCancelIfFailed(stageContext);

    assertThat(result).isTrue();
  }

  private static EntitlementRequest entitlementRequest(boolean purgeOnRollback) {
    return EntitlementRequest.builder()
      .type(REVOKE)
      .tenantId(TENANT_ID)
      .applications(List.of(APPLICATION_ID))
      .purgeOnRollback(purgeOnRollback)
      .build();
  }
}
