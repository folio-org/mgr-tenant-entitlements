package org.folio.entitlement.integration.keycloak;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.REVOKE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.moduleFlowParameters;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.token.TokenManager;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakModuleResourceCleanerTest {

  @InjectMocks private KeycloakModuleResourceCleaner keycloakModuleResourceCleaner;
  @Mock private Keycloak keycloakClient;
  @Mock private TokenManager tokenManager;
  @Mock private KeycloakService keycloakService;
  @Mock private ThreadLocalModuleStageContext threadLocalModuleStageContext;

  @BeforeEach
  void setup() {
    keycloakModuleResourceCleaner.setThreadLocalModuleStageContext(threadLocalModuleStageContext);
  }

  @AfterEach
  void tearDown() {
    reset(threadLocalModuleStageContext);
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_purgeTrue() {
    when(keycloakClient.tokenManager()).thenReturn(tokenManager);
    when(tokenManager.grantToken()).thenReturn(null);

    var request = EntitlementRequest.builder().type(REVOKE).purge(true).build();
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = moduleFlowParameters(request, moduleDescriptor());
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    keycloakModuleResourceCleaner.execute(stageContext);

    verify(keycloakClient).tokenManager();
    verify(keycloakService).removeAuthResources(moduleDescriptor(), TENANT_NAME);
  }

  @Test
  void execute_positive_purgeFalse() {
    var request = EntitlementRequest.builder().type(REVOKE).purge(false).build();
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = moduleFlowParameters(request, moduleDescriptor());
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    keycloakModuleResourceCleaner.execute(stageContext);

    verify(keycloakClient, never()).tokenManager();
    verify(keycloakService, never()).removeAuthResources(any(), any());
  }

  @Test
  void getStageName_positive() {
    var request = EntitlementRequest.builder().type(REVOKE).purge(false).build();
    var flowParameters = moduleFlowParameters(request, moduleDescriptor());
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var result = keycloakModuleResourceCleaner.getStageName(stageContext);
    assertThat(result).isEqualTo("mod-foo-1.0.0-keycloakModuleResourceCleaner");
  }

  private static ModuleDescriptor moduleDescriptor() {
    return new ModuleDescriptor().id("mod-foo-1.0.0").description("Foo module");
  }
}
