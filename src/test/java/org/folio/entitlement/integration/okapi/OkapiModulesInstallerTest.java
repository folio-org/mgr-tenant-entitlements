package org.folio.entitlement.integration.okapi;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.REVOKE;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor.ActionType.DISABLE;
import static org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor.ActionType.ENABLE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.TENANT_PARAMETERS;
import static org.folio.entitlement.support.TestValues.okapiStageContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor;
import org.folio.entitlement.integration.okapi.stage.OkapiModulesInstaller;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OkapiModulesInstallerTest {

  private static final String MODULE_ID = "mod-bar-1.7.9";

  @InjectMocks private OkapiModulesInstaller okapiModulesInstaller;
  @Mock private OkapiClient okapiClient;
  @Mock private EntitlementModuleService moduleService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_entitleRequest() {
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = Map.of(
      PARAM_REQUEST, entitlementRequest(),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_MODULE_DESCRIPTORS, List.of(moduleDescriptor()));
    var stageContext = okapiStageContext(FLOW_STAGE_ID, flowParameters, contextData);

    okapiModulesInstaller.execute(stageContext);

    var expectedRequest = List.of(new TenantModuleDescriptor().id(MODULE_ID).action(ENABLE));
    verify(moduleService).saveAll(TENANT_ID, APPLICATION_ID, List.of(MODULE_ID));
    verify(okapiClient).installTenantModules(
      TENANT_NAME, false, false, TENANT_PARAMETERS, true, false, expectedRequest, OKAPI_TOKEN);
  }

  @Test
  void execute_positive_revokeRequest() {
    var parameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = Map.of(
      PARAM_REQUEST, entitlementRevokeRequest(),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_MODULE_DESCRIPTORS, List.of(moduleDescriptor()));

    var stageContext = okapiStageContext(FLOW_STAGE_ID, flowParameters, parameters);

    okapiModulesInstaller.execute(stageContext);

    var request = List.of(new TenantModuleDescriptor().id(MODULE_ID).action(DISABLE));
    verify(moduleService).deleteAll(TENANT_ID, APPLICATION_ID, List.of(MODULE_ID));
    verify(okapiClient).installTenantModules(
      TENANT_NAME, false, true, TENANT_PARAMETERS, true, false, request, OKAPI_TOKEN);
  }

  @Test
  void execute_negative_okapiClientThrowsError() {
    var contextParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = Map.of(
      PARAM_REQUEST, entitlementRequest(),
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_MODULE_DESCRIPTORS, List.of(moduleDescriptor()));

    doThrow(new RuntimeException("exception")).when(okapiClient)
      .installTenantModules(anyString(), anyBoolean(), anyBoolean(), any(), anyBoolean(), anyBoolean(), any(), any());

    var context = okapiStageContext("flowId", flowParameters, contextParameters);

    assertThatThrownBy(() -> okapiModulesInstaller.execute(context))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("exception");

    verify(moduleService).saveAll(TENANT_ID, APPLICATION_ID, List.of(MODULE_ID));
  }

  public static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder()
      .type(ENTITLE)
      .okapiToken(OKAPI_TOKEN)
      .tenantParameters(TENANT_PARAMETERS)
      .tenantId(TENANT_ID)
      .applications(List.of(APPLICATION_ID))
      .ignoreErrors(true)
      .async(false)
      .build();
  }

  public static ModuleDescriptor moduleDescriptor() {
    return new ModuleDescriptor().id(MODULE_ID);
  }

  private static EntitlementRequest entitlementRevokeRequest() {
    return EntitlementRequest.builder()
      .type(REVOKE)
      .okapiToken(OKAPI_TOKEN)
      .tenantParameters(TENANT_PARAMETERS)
      .tenantId(TENANT_ID)
      .applications(List.of(APPLICATION_ID))
      .ignoreErrors(true)
      .purge(true)
      .async(false)
      .build();
  }
}
