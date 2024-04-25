package org.folio.entitlement.integration.keycloak;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.moduleFlowParameters;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
class KeycloakModuleResourceCreatorTest {

  @InjectMocks private KeycloakModuleResourceCreator keycloakModuleResourceCreator;
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

    var moduleDescriptor = moduleDescriptor();
    var flowParameters = moduleFlowParameters(entitlementRequest(true), moduleDescriptor);
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    keycloakModuleResourceCreator.execute(stageContext);

    verify(keycloakService).updateAuthResources(null, moduleDescriptor, TENANT_NAME);
  }

  @Test
  void cancel_positive_purgeOnRollback() {
    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    var moduleDescriptor = moduleDescriptor();
    var flowParameters = moduleFlowParameters(entitlementRequest(true), moduleDescriptor);
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    keycloakModuleResourceCreator.cancel(stageContext);

    verify(keycloakService).removeAuthResources(moduleDescriptor, TENANT_NAME);
  }

  @Test
  void cancel_positive_purgeOnRollbackIsFalse() {
    var flowParameters = moduleFlowParameters(entitlementRequest(false), moduleDescriptor());
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    keycloakModuleResourceCreator.cancel(stageContext);

    verifyNoInteractions(keycloakService);
  }

  @Test
  void shouldCancelIfFailed_positive() {
    var flowParameters = moduleFlowParameters(entitlementRequest(false), moduleDescriptor());
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    var result = keycloakModuleResourceCreator.shouldCancelIfFailed(stageContext);
    assertThat(result).isTrue();
  }

  @Test
  void getStageName_positive() {
    var flowParameters = moduleFlowParameters(entitlementRequest(false), moduleDescriptor());
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var result = keycloakModuleResourceCreator.getStageName(stageContext);
    assertThat(result).isEqualTo("mod-foo-1.0.0-keycloakModuleResourceCreator");
  }

  private static EntitlementRequest entitlementRequest(boolean purgeOnRollback) {
    return EntitlementRequest.builder()
      .type(ENTITLE)
      .tenantId(TENANT_ID)
      .applications(List.of(APPLICATION_ID))
      .purgeOnRollback(purgeOnRollback)
      .build();
  }

  private static ModuleDescriptor moduleDescriptor() {
    return new ModuleDescriptor().id("mod-foo-1.0.0");
  }
}
