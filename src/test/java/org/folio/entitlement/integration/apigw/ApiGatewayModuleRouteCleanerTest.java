package org.folio.entitlement.integration.apigw;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.REVOKE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.integration.kafka.model.ModuleType.MODULE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.moduleFlowParameters;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.folio.tools.kong.service.KongGatewayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApiGatewayModuleRouteCleanerTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";

  @Mock private KongGatewayService kongGatewayService;
  @Mock private EntitlementModuleService entitlementModuleService;

  private ApiGatewayConfigurationProperties properties;
  private ApiGatewayModuleRouteCleaner apiGatewayModuleRouteCleaner;

  @BeforeEach
  void setUp() {
    properties = mock(ApiGatewayConfigurationProperties.class, Answers.RETURNS_DEEP_STUBS);
    apiGatewayModuleRouteCleaner = new ApiGatewayModuleRouteCleaner(kongGatewayService, properties,
      entitlementModuleService);
  }

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_lastTenant_routeManagementEnabled() {
    when(properties.getRouteManagement().isEnabled()).thenReturn(true);
    when(entitlementModuleService.isEntitlementExist(MODULE_ID)).thenReturn(false);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowParams(), stageParams());

    apiGatewayModuleRouteCleaner.execute(stageContext);

    verify(kongGatewayService).deleteServiceRoutes(MODULE_ID);
    verify(kongGatewayService).deleteService(MODULE_ID);
  }

  @Test
  void execute_positive_lastTenant_routeManagementDisabled() {
    when(properties.getRouteManagement().isEnabled()).thenReturn(false);
    when(properties.getTenantChecks().isEnabled()).thenReturn(false);
    when(entitlementModuleService.isEntitlementExist(MODULE_ID)).thenReturn(false);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowParams(), stageParams());

    apiGatewayModuleRouteCleaner.execute(stageContext);

    verifyNoInteractions(kongGatewayService);
  }

  @Test
  void execute_positive_tenantChecksEnabled_notLastTenant_removesOnlyTenantFilter() {
    when(properties.getTenantChecks().isEnabled()).thenReturn(true);
    when(entitlementModuleService.isEntitlementExist(MODULE_ID)).thenReturn(true);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowParams(), stageParams());

    apiGatewayModuleRouteCleaner.execute(stageContext);

    verify(kongGatewayService).removeTenantFromModuleRoutes(MODULE_ID, TENANT_NAME);
  }

  @Test
  void execute_positive_uiModule() {
    var flowParams = moduleFlowParameters(entitlementRequest(), UI_MODULE, new ModuleDescriptor().id(MODULE_ID));
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParams, stageParams());

    apiGatewayModuleRouteCleaner.execute(stageContext);

    verifyNoInteractions(kongGatewayService, entitlementModuleService);
  }

  @Test
  void execute_positive_lastTenant_serviceNotFound_skipsCleanup() {
    when(properties.getRouteManagement().isEnabled()).thenReturn(true);
    when(entitlementModuleService.isEntitlementExist(MODULE_ID)).thenReturn(false);
    doThrow(new NoSuchElementException()).when(kongGatewayService).deleteServiceRoutes(MODULE_ID);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowParams(), stageParams());

    assertThatCode(() -> apiGatewayModuleRouteCleaner.execute(stageContext)).doesNotThrowAnyException();

    verify(kongGatewayService).deleteServiceRoutes(MODULE_ID);
  }

  @Test
  void getStageName_positive() {
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowParams(), emptyMap());

    assertThat(apiGatewayModuleRouteCleaner.getStageName(stageContext))
      .isEqualTo(MODULE_ID + "-apiGatewayModuleRouteCleaner");
  }

  private static Map<?, ?> moduleFlowParams() {
    return moduleFlowParameters(entitlementRequest(), MODULE, new ModuleDescriptor().id(MODULE_ID));
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder()
      .type(REVOKE)
      .tenantId(TENANT_ID)
      .applications(List.of(APPLICATION_ID))
      .build();
  }

  private static Map<String, String> stageParams() {
    return Map.of(PARAM_TENANT_NAME, TENANT_NAME);
  }
}
