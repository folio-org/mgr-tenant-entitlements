package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor.ActionType.DISABLE;
import static org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor.ActionType.ENABLE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.TENANT_PARAMETERS;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.folio.entitlement.support.TestValues.simpleApplicationDescriptor;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.okapi.OkapiClient;
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
class OkapiModuleInstallerTest {

  private static final String MODULE_ID = "mod-bar-1.7.9";

  @InjectMocks private OkapiModulesInstaller okapiModulesInstaller;
  @Mock private OkapiClient okapiClient;
  @Mock private EntitlementModuleService moduleService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_installRequest() {
    var applicationDescriptor = simpleApplicationDescriptor(APPLICATION_ID);
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = flowParameters(entitlementRequest(), applicationDescriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, contextData);

    okapiModulesInstaller.execute(stageContext);

    var expectedRequest = List.of(new TenantModuleDescriptor().id("mod-bar-1.7.9").action(ENABLE));
    verify(moduleService).saveAll(TENANT_ID, APPLICATION_ID, List.of(MODULE_ID));
    verify(okapiClient).installTenantModules(
      TENANT_NAME, false, false, TENANT_PARAMETERS, true, false, expectedRequest, OKAPI_TOKEN);
  }

  @Test
  void execute_positive_uninstallRequest() {
    var descriptor = simpleApplicationDescriptor(APPLICATION_ID);
    var parameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = flowParameters(entitlementRevokeRequest(), descriptor);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, parameters);

    okapiModulesInstaller.execute(stageContext);

    var request = List.of(new TenantModuleDescriptor().id("mod-bar-1.7.9").action(DISABLE));
    verify(moduleService).deleteAll(TENANT_ID, APPLICATION_ID, List.of(MODULE_ID));
    verify(okapiClient).installTenantModules(
      TENANT_NAME, false, true, TENANT_PARAMETERS, true, false, request, OKAPI_TOKEN);
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
