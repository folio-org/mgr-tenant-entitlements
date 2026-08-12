package org.folio.entitlement.integration.kong;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.dto.Entitlements;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.folio.tools.kong.service.KongGatewayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KongModuleRouteCleanerTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";

  @Mock private KongGatewayService kongGatewayService;
  @Mock private EntitlementModuleService entitlementModuleService;
  @Mock private ThreadLocalModuleStageContext threadLocalModuleStageContext;

  private KongModuleRouteCleaner kongModuleRouteCleaner;

  @BeforeEach
  void setUp() {
    kongModuleRouteCleaner = new KongModuleRouteCleaner(kongGatewayService,
      defaultProperties(), entitlementModuleService);
    kongModuleRouteCleaner.setThreadLocalModuleStageContext(threadLocalModuleStageContext);
  }

  @AfterEach
  void tearDown() {
    reset(threadLocalModuleStageContext);
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_lastTenant_routeManagementEnabled() {
    when(entitlementModuleService.getModuleEntitlements(MODULE_ID, 2, 0))
      .thenReturn(new Entitlements().totalRecords(1));

    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowParams(), stageParams());

    kongModuleRouteCleaner.execute(stageContext);

    verify(entitlementModuleService).getModuleEntitlements(MODULE_ID, 2, 0);
    verify(kongGatewayService).deleteServiceRoutes(MODULE_ID);
    verify(kongGatewayService).deleteService(MODULE_ID);
  }

  @Test
  void execute_positive_notLastTenant_noOp() {
    when(entitlementModuleService.getModuleEntitlements(MODULE_ID, 2, 0))
      .thenReturn(new Entitlements().totalRecords(2));

    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowParams(), stageParams());

    kongModuleRouteCleaner.execute(stageContext);

    verify(entitlementModuleService).getModuleEntitlements(MODULE_ID, 2, 0);
    verifyNoInteractions(kongGatewayService);
  }

  @Test
  void execute_positive_lastTenant_routeManagementDisabled() {
    var cleaner = new KongModuleRouteCleaner(kongGatewayService,
      propertiesWithoutRouteManagement(), entitlementModuleService);
    cleaner.setThreadLocalModuleStageContext(threadLocalModuleStageContext);

    when(entitlementModuleService.getModuleEntitlements(MODULE_ID, 2, 0))
      .thenReturn(new Entitlements().totalRecords(1));

    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowParams(), stageParams());

    cleaner.execute(stageContext);

    verify(entitlementModuleService).getModuleEntitlements(MODULE_ID, 2, 0);
    verifyNoInteractions(kongGatewayService);
  }

  @Test
  void execute_positive_tenantChecksEnabled_removesOnlyTenantFilter() {
    var cleaner = new KongModuleRouteCleaner(kongGatewayService,
      propertiesWithTenantChecks(), entitlementModuleService);
    cleaner.setThreadLocalModuleStageContext(threadLocalModuleStageContext);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowParams(), stageParams());

    cleaner.execute(stageContext);

    verify(kongGatewayService).removeTenantFromModuleRoutes(MODULE_ID, TENANT_NAME);
    verifyNoInteractions(entitlementModuleService);
  }

  @Test
  void execute_positive_uiModule() {
    var flowParams = moduleFlowParameters(entitlementRequest(), UI_MODULE, new ModuleDescriptor().id(MODULE_ID));
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParams, stageParams());

    kongModuleRouteCleaner.execute(stageContext);

    verifyNoInteractions(kongGatewayService, entitlementModuleService);
  }

  @Test
  void execute_positive_lastTenant_deleteServiceRoutesThrowsNoSuchElement() {
    when(entitlementModuleService.getModuleEntitlements(MODULE_ID, 2, 0))
      .thenReturn(new Entitlements().totalRecords(1));
    org.mockito.Mockito.doThrow(new NoSuchElementException())
      .when(kongGatewayService).deleteServiceRoutes(MODULE_ID);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowParams(), stageParams());

    kongModuleRouteCleaner.execute(stageContext);

    verify(entitlementModuleService).getModuleEntitlements(MODULE_ID, 2, 0);
    verify(kongGatewayService).deleteServiceRoutes(MODULE_ID);
    verify(kongGatewayService).deleteService(MODULE_ID);
  }

  @Test
  void getStageName_positive() {
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowParams(), emptyMap());

    assertThat(kongModuleRouteCleaner.getStageName(stageContext)).isEqualTo(MODULE_ID + "-kongModuleRouteCleaner");
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

  private static ApiGatewayConfigurationProperties defaultProperties() {
    return new ApiGatewayConfigurationProperties();
  }

  private static ApiGatewayConfigurationProperties propertiesWithTenantChecks() {
    var props = new ApiGatewayConfigurationProperties();
    props.getTenantChecks().setEnabled(true);
    return props;
  }

  private static ApiGatewayConfigurationProperties propertiesWithoutRouteManagement() {
    var props = new ApiGatewayConfigurationProperties();
    props.getRouteManagement().setEnabled(false);
    return props;
  }
}
