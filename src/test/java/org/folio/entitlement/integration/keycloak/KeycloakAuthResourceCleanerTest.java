package org.folio.entitlement.integration.keycloak;

import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.simpleApplicationDescriptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
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

  @Test
  void execute_positive_purgeTrue() {
    var request = EntitlementRequest.builder().type(REVOKE).purge(true).build();
    var applicationDescriptor = simpleApplicationDescriptor(APPLICATION_ID);
    var stageParameters = Map.of(PARAM_APP_DESCRIPTOR, applicationDescriptor, PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = StageContext.of(FLOW_STAGE_ID, Map.of(PARAM_REQUEST, request), stageParameters);

    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    keycloakAuthResourceCleaner.execute(stageContext);

    verify(keycloakClient).tokenManager();
    verify(keycloakService).unregisterModuleResources(applicationDescriptor.getModuleDescriptors().get(0), TENANT_NAME);
  }

  @Test
  void execute_positive_purgeFalse() {
    var request = EntitlementRequest.builder().type(REVOKE).purge(false).build();
    var applicationDescriptor = applicationDescriptor();
    var stageParameters = Map.of(PARAM_APP_DESCRIPTOR, applicationDescriptor, PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = StageContext.of(FLOW_STAGE_ID, Map.of(PARAM_REQUEST, request), stageParameters);

    keycloakAuthResourceCleaner.execute(stageContext);

    verify(keycloakClient, never()).tokenManager();
    verify(keycloakService, never()).unregisterModuleResources(any(), any());
  }
}
