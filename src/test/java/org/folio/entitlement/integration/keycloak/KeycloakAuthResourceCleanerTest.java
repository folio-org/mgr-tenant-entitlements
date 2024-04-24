package org.folio.entitlement.integration.keycloak;

import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.folio.entitlement.support.TestValues.simpleApplicationDescriptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
class KeycloakAuthResourceCleanerTest {

  @InjectMocks private KeycloakAuthResourceCleaner keycloakAuthResourceCleaner;
  @Mock private Keycloak keycloakClient;
  @Mock private KeycloakService keycloakService;
  @Mock private TokenManager tokenManager;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_purgeTrue() {
    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    var request = EntitlementRequest.builder().type(REVOKE).purge(true).build();
    var applicationDescriptor = simpleApplicationDescriptor(APPLICATION_ID);
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = flowParameters(request, applicationDescriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    keycloakAuthResourceCleaner.execute(stageContext);

    verify(keycloakClient).tokenManager();
    verify(keycloakService).removeAuthResources(applicationDescriptor.getModuleDescriptors().get(0), TENANT_NAME);
  }

  @Test
  void execute_positive_purgeFalse() {
    var request = EntitlementRequest.builder().type(REVOKE).purge(false).build();
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = flowParameters(request, applicationDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    keycloakAuthResourceCleaner.execute(stageContext);

    verify(keycloakClient, never()).tokenManager();
    verify(keycloakService, never()).removeAuthResources(any(), any());
  }
}
