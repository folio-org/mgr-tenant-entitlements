package org.folio.entitlement.integration.kong;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_INSTALLED_MODULE_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_DISCOVERY;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.dto.Entitlements;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.folio.tools.kong.model.Service;
import org.folio.tools.kong.service.KongGatewayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KongModuleRouteUpdaterTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String MODULE_LOCATION = "http://mod-foo:8080";
  private static final String INSTALLED_MODULE_ID = "mod-foo-0.9.0";

  @Mock private KongGatewayService kongGatewayService;
  @Mock private EntitlementModuleService entitlementModuleService;
  @Mock private ThreadLocalModuleStageContext threadLocalModuleStageContext;

  private KongModuleRouteUpdater kongModuleRouteUpdater;

  @BeforeEach
  void setUp() {
    kongModuleRouteUpdater = new KongModuleRouteUpdater(kongGatewayService,
      defaultProperties(), entitlementModuleService);
    kongModuleRouteUpdater.setThreadLocalModuleStageContext(threadLocalModuleStageContext);
  }

  @AfterEach
  void tearDown() {
    reset(threadLocalModuleStageContext);
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_routeManagementEnabled_tenantChecksDisabled() {
    when(entitlementModuleService.isEntitlementExist(MODULE_ID)).thenReturn(false);

    var descriptor = moduleDescriptor();
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(descriptor), stageParams());

    kongModuleRouteUpdater.execute(stageContext);

    verify(entitlementModuleService).isEntitlementExist(MODULE_ID);
    verify(kongGatewayService).upsertService(new Service().name(MODULE_ID).url(MODULE_LOCATION));
    verify(kongGatewayService).addRoutes(List.of(descriptor));
  }

  @Test
  void execute_positive_tenantChecksEnabled() {
    when(entitlementModuleService.isEntitlementExist(MODULE_ID)).thenReturn(false);

    var updater = new KongModuleRouteUpdater(kongGatewayService,
      propertiesWithTenantChecks(), entitlementModuleService);
    updater.setThreadLocalModuleStageContext(threadLocalModuleStageContext);

    var descriptor = moduleDescriptor();
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(descriptor), stageParams());

    updater.execute(stageContext);

    verify(entitlementModuleService).isEntitlementExist(MODULE_ID);
    verify(kongGatewayService).upsertService(new Service().name(MODULE_ID).url(MODULE_LOCATION));
    verify(kongGatewayService).addRoutes(List.of(descriptor));
    verify(kongGatewayService).addTenantToModuleRoutes(MODULE_ID, TENANT_NAME);
  }

  @Test
  void execute_positive_routeManagementDisabled() {
    var updater = new KongModuleRouteUpdater(kongGatewayService,
      propertiesWithoutRouteManagement(), entitlementModuleService);
    updater.setThreadLocalModuleStageContext(threadLocalModuleStageContext);

    var descriptor = moduleDescriptor();
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(descriptor), stageParams());

    updater.execute(stageContext);

    verify(kongGatewayService).upsertService(new Service().name(MODULE_ID).url(MODULE_LOCATION));
  }

  @Test
  void execute_positive_uiModule() {
    var descriptor = moduleDescriptor();
    var flowParams = moduleFlowParameters(entitlementRequest(), UI_MODULE, descriptor);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParams, stageParams());

    kongModuleRouteUpdater.execute(stageContext);

    verifyNoInteractions(kongGatewayService);
  }

  @Test
  void execute_positive_deprecatedModule_lastTenant_routeManagementEnabled() {
    when(entitlementModuleService.getModuleEntitlements(MODULE_ID, 2, 0))
      .thenReturn(new Entitlements().totalRecords(1));

    var stageContext = moduleStageContext(FLOW_STAGE_ID,
      moduleFlowParameters(entitlementRequest(), MODULE, moduleDescriptor()), stageParams());

    kongModuleRouteUpdater.execute(stageContext);

    verify(entitlementModuleService).getModuleEntitlements(MODULE_ID, 2, 0);
    verify(kongGatewayService).deleteServiceRoutes(MODULE_ID);
    verify(kongGatewayService).deleteService(MODULE_ID);
  }

  @Test
  void execute_positive_deprecatedModule_notLastTenant_noOp() {
    when(entitlementModuleService.getModuleEntitlements(MODULE_ID, 2, 0))
      .thenReturn(new Entitlements().totalRecords(2));

    var stageContext = moduleStageContext(FLOW_STAGE_ID,
      moduleFlowParameters(entitlementRequest(), MODULE, moduleDescriptor()), stageParams());

    kongModuleRouteUpdater.execute(stageContext);

    verify(entitlementModuleService).getModuleEntitlements(MODULE_ID, 2, 0);
    verifyNoInteractions(kongGatewayService);
  }

  @Test
  void execute_positive_deprecatedModule_lastTenant_routeManagementDisabled() {
    var updater = new KongModuleRouteUpdater(kongGatewayService,
      propertiesWithoutRouteManagement(), entitlementModuleService);
    updater.setThreadLocalModuleStageContext(threadLocalModuleStageContext);

    when(entitlementModuleService.getModuleEntitlements(MODULE_ID, 2, 0))
      .thenReturn(new Entitlements().totalRecords(1));

    var stageContext = moduleStageContext(FLOW_STAGE_ID,
      moduleFlowParameters(entitlementRequest(), MODULE, moduleDescriptor()), stageParams());

    updater.execute(stageContext);

    verify(entitlementModuleService).getModuleEntitlements(MODULE_ID, 2, 0);
    verifyNoInteractions(kongGatewayService);
  }

  @Test
  void execute_positive_moduleNotUpdated_onlyUpsertsService() {
    var descriptor = moduleDescriptor();
    var installed = moduleDescriptor();

    var stageContext = moduleStageContext(FLOW_STAGE_ID,
      moduleFlowWithDiscoveryAndInstalled(descriptor, installed), stageParams());

    kongModuleRouteUpdater.execute(stageContext);

    verify(kongGatewayService).upsertService(new Service().name(MODULE_ID).url(MODULE_LOCATION));
  }

  @Test
  void execute_positive_tenantChecksEnabled_withInstalledModule_removesTenantFromOldRoutes() {
    when(entitlementModuleService.isEntitlementExist(MODULE_ID)).thenReturn(false);

    var updater = new KongModuleRouteUpdater(kongGatewayService,
      propertiesWithTenantChecks(), entitlementModuleService);
    updater.setThreadLocalModuleStageContext(threadLocalModuleStageContext);

    var descriptor = moduleDescriptor();
    var installed = new ModuleDescriptor().id(INSTALLED_MODULE_ID);

    var stageContext = moduleStageContext(FLOW_STAGE_ID,
      moduleFlowWithDiscoveryAndInstalled(descriptor, installed), stageParams());

    updater.execute(stageContext);

    verify(entitlementModuleService).isEntitlementExist(MODULE_ID);
    verify(kongGatewayService).upsertService(new Service().name(MODULE_ID).url(MODULE_LOCATION));
    verify(kongGatewayService).removeTenantFromModuleRoutes(INSTALLED_MODULE_ID, TENANT_NAME);
    verify(kongGatewayService).addRoutes(List.of(descriptor));
    verify(kongGatewayService).addTenantToModuleRoutes(MODULE_ID, TENANT_NAME);
  }

  @Test
  void execute_positive_routeManagementEnabled_entitlementExists_skipsRouteCreation() {
    when(entitlementModuleService.isEntitlementExist(MODULE_ID)).thenReturn(true);

    var descriptor = moduleDescriptor();
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(descriptor), stageParams());

    kongModuleRouteUpdater.execute(stageContext);

    verify(entitlementModuleService).isEntitlementExist(MODULE_ID);
    verify(kongGatewayService).upsertService(new Service().name(MODULE_ID).url(MODULE_LOCATION));
  }

  @Test
  void getStageName_positive() {
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(moduleDescriptor()), emptyMap());

    assertThat(kongModuleRouteUpdater.getStageName(stageContext)).isEqualTo(MODULE_ID + "-kongModuleRouteUpdater");
  }

  private static ModuleDescriptor moduleDescriptor() {
    return new ModuleDescriptor().id(MODULE_ID);
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder()
      .type(UPGRADE)
      .tenantId(TENANT_ID)
      .applications(List.of(APPLICATION_ID))
      .build();
  }

  private static Map<?, ?> moduleFlowWithDiscovery(ModuleDescriptor descriptor) {
    var params = new HashMap<Object, Object>(moduleFlowParameters(entitlementRequest(), MODULE, descriptor));
    params.put(PARAM_MODULE_DISCOVERY, MODULE_LOCATION);
    return params;
  }

  private static Map<?, ?> moduleFlowWithDiscoveryAndInstalled(ModuleDescriptor descriptor,
      ModuleDescriptor installed) {
    var params = new HashMap<Object, Object>(moduleFlowWithDiscovery(descriptor));
    params.put(PARAM_INSTALLED_MODULE_DESCRIPTOR, installed);
    return params;
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
