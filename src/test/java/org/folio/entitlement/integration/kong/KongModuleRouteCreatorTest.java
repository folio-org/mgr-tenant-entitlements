package org.folio.entitlement.integration.kong;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_DISCOVERY;
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
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.folio.tools.kong.model.Service;
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
class KongModuleRouteCreatorTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String MODULE_LOCATION = "http://mod-foo:8080";

  @Mock private KongGatewayService kongGatewayService;
  @Mock private EntitlementModuleService entitlementModuleService;

  private ApiGatewayConfigurationProperties properties;
  private KongModuleRouteCreator kongModuleRouteCreator;

  @BeforeEach
  void setUp() {
    properties = mock(ApiGatewayConfigurationProperties.class, Answers.RETURNS_DEEP_STUBS);
    kongModuleRouteCreator = new KongModuleRouteCreator(kongGatewayService, properties, entitlementModuleService);
  }

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_routeManagementEnabled_tenantChecksDisabled() {
    when(properties.getRouteManagement().isEnabled()).thenReturn(true);
    when(properties.getTenantChecks().isEnabled()).thenReturn(false);

    var descriptor = moduleDescriptor();
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(descriptor), stageParams());

    kongModuleRouteCreator.execute(stageContext);

    verify(entitlementModuleService).isEntitlementExist(MODULE_ID);
    verify(kongGatewayService).upsertService(new Service().name(MODULE_ID).url(MODULE_LOCATION));
    verify(kongGatewayService).addRoutes(List.of(descriptor));
  }

  @Test
  void execute_positive_tenantChecksEnabled() {
    when(properties.getRouteManagement().isEnabled()).thenReturn(true);
    when(properties.getTenantChecks().isEnabled()).thenReturn(true);

    var descriptor = moduleDescriptor();
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(descriptor), stageParams());

    kongModuleRouteCreator.execute(stageContext);

    verify(entitlementModuleService).isEntitlementExist(MODULE_ID);
    verify(kongGatewayService).upsertService(new Service().name(MODULE_ID).url(MODULE_LOCATION));
    verify(kongGatewayService).addRoutes(List.of(descriptor));
    verify(kongGatewayService).addTenantToModuleRoutes(MODULE_ID, TENANT_NAME);
  }

  @Test
  void execute_positive_routeManagementDisabled() {
    when(properties.getRouteManagement().isEnabled()).thenReturn(false);
    when(properties.getTenantChecks().isEnabled()).thenReturn(false);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(moduleDescriptor()), stageParams());

    kongModuleRouteCreator.execute(stageContext);

    verify(kongGatewayService).upsertService(new Service().name(MODULE_ID).url(MODULE_LOCATION));
  }

  @Test
  void execute_positive_uiModule() {
    var stageContext = moduleStageContext(FLOW_STAGE_ID,
      moduleFlowParameters(entitlementRequest(), UI_MODULE, moduleDescriptor()), stageParams());

    kongModuleRouteCreator.execute(stageContext);

    verifyNoInteractions(kongGatewayService, entitlementModuleService);
  }

  @Test
  void cancel_positive_routeManagementEnabled() {
    when(properties.getRouteManagement().isEnabled()).thenReturn(true);
    when(entitlementModuleService.isEntitlementExist(MODULE_ID)).thenReturn(false);

    var stageContext = moduleStageContext(FLOW_STAGE_ID,
      moduleFlowWithDiscovery(moduleDescriptor(), true), stageParams());

    kongModuleRouteCreator.cancel(stageContext);

    verify(entitlementModuleService).isEntitlementExist(MODULE_ID);
    verify(kongGatewayService).deleteServiceRoutes(MODULE_ID);
    verify(kongGatewayService).deleteService(MODULE_ID);
  }

  @Test
  void cancel_positive_routeManagementDisabled() {
    when(properties.getRouteManagement().isEnabled()).thenReturn(false);
    when(properties.getTenantChecks().isEnabled()).thenReturn(false);

    var stageContext = moduleStageContext(FLOW_STAGE_ID,
      moduleFlowWithDiscovery(moduleDescriptor(), true), stageParams());

    kongModuleRouteCreator.cancel(stageContext);

    verifyNoInteractions(kongGatewayService, entitlementModuleService);
  }

  @Test
  void cancel_positive_routeManagementEnabled_serviceNotFound() {
    when(properties.getRouteManagement().isEnabled()).thenReturn(true);
    when(entitlementModuleService.isEntitlementExist(MODULE_ID)).thenReturn(false);
    doThrow(new NoSuchElementException()).when(kongGatewayService).deleteServiceRoutes(MODULE_ID);

    var stageContext = moduleStageContext(FLOW_STAGE_ID,
      moduleFlowWithDiscovery(moduleDescriptor(), true), stageParams());

    assertThatCode(() -> kongModuleRouteCreator.cancel(stageContext)).doesNotThrowAnyException();

    verify(kongGatewayService).deleteServiceRoutes(MODULE_ID);
    verify(kongGatewayService).deleteService(MODULE_ID);
  }

  @Test
  void cancel_positive_routeManagementEnabled_deleteServiceFails() {
    when(properties.getRouteManagement().isEnabled()).thenReturn(true);
    when(entitlementModuleService.isEntitlementExist(MODULE_ID)).thenReturn(false);
    doThrow(new RuntimeException("kong error")).when(kongGatewayService).deleteService(MODULE_ID);

    var stageContext = moduleStageContext(FLOW_STAGE_ID,
      moduleFlowWithDiscovery(moduleDescriptor(), true), stageParams());

    assertThatCode(() -> kongModuleRouteCreator.cancel(stageContext)).doesNotThrowAnyException();

    verify(kongGatewayService).deleteServiceRoutes(MODULE_ID);
    verify(kongGatewayService).deleteService(MODULE_ID);
  }

  @Test
  void cancel_positive_tenantChecksEnabled() {
    when(properties.getRouteManagement().isEnabled()).thenReturn(true);
    when(properties.getTenantChecks().isEnabled()).thenReturn(true);
    when(entitlementModuleService.isEntitlementExist(MODULE_ID)).thenReturn(true);

    var stageContext = moduleStageContext(FLOW_STAGE_ID,
      moduleFlowWithDiscovery(moduleDescriptor(), true), stageParams());

    kongModuleRouteCreator.cancel(stageContext);

    verify(entitlementModuleService).isEntitlementExist(MODULE_ID);
    verify(kongGatewayService).removeTenantFromModuleRoutes(MODULE_ID, TENANT_NAME);
  }

  @Test
  void cancel_positive_routeManagementEnabled_notLastTenant() {
    when(properties.getRouteManagement().isEnabled()).thenReturn(true);
    when(properties.getTenantChecks().isEnabled()).thenReturn(false);
    when(entitlementModuleService.isEntitlementExist(MODULE_ID)).thenReturn(true);

    var stageContext = moduleStageContext(FLOW_STAGE_ID,
      moduleFlowWithDiscovery(moduleDescriptor(), true), stageParams());

    kongModuleRouteCreator.cancel(stageContext);

    verify(entitlementModuleService).isEntitlementExist(MODULE_ID);
    verifyNoInteractions(kongGatewayService);
  }

  @Test
  void cancel_positive_tenantChecksEnabled_removeTenantThrowsException() {
    when(properties.getRouteManagement().isEnabled()).thenReturn(true);
    when(properties.getTenantChecks().isEnabled()).thenReturn(true);
    when(entitlementModuleService.isEntitlementExist(MODULE_ID)).thenReturn(true);
    doThrow(new RuntimeException("kong error")).when(kongGatewayService)
      .removeTenantFromModuleRoutes(MODULE_ID, TENANT_NAME);

    var stageContext = moduleStageContext(FLOW_STAGE_ID,
      moduleFlowWithDiscovery(moduleDescriptor(), true), stageParams());

    kongModuleRouteCreator.cancel(stageContext);

    verify(kongGatewayService).removeTenantFromModuleRoutes(MODULE_ID, TENANT_NAME);
  }

  @Test
  void cancel_positive_purgeOnRollbackDisabled() {
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(moduleDescriptor()), stageParams());

    kongModuleRouteCreator.cancel(stageContext);

    verifyNoInteractions(kongGatewayService, entitlementModuleService);
  }

  @Test
  void cancel_positive_uiModule() {
    var stageContext = moduleStageContext(FLOW_STAGE_ID,
      moduleFlowParameters(entitlementRequest(), UI_MODULE, moduleDescriptor()), stageParams());

    kongModuleRouteCreator.cancel(stageContext);

    verifyNoInteractions(kongGatewayService, entitlementModuleService);
  }

  @Test
  void shouldCancelIfFailed_positive() {
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(moduleDescriptor()), stageParams());

    var result = kongModuleRouteCreator.shouldCancelIfFailed(stageContext);

    assertThat(result).isTrue();
  }

  @Test
  void getStageName_positive() {
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(moduleDescriptor()), emptyMap());

    var stageName = kongModuleRouteCreator.getStageName(stageContext);

    assertThat(stageName).isEqualTo(MODULE_ID + "-kongModuleRouteCreator");
  }

  private static ModuleDescriptor moduleDescriptor() {
    return new ModuleDescriptor().id(MODULE_ID);
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder()
      .type(ENTITLE)
      .tenantId(TENANT_ID)
      .applications(List.of(APPLICATION_ID))
      .build();
  }

  private static Map<?, ?> moduleFlowWithDiscovery(ModuleDescriptor descriptor) {
    return moduleFlowWithDiscovery(descriptor, false);
  }

  private static Map<?, ?> moduleFlowWithDiscovery(ModuleDescriptor descriptor, boolean purgeOnRollback) {
    var request = EntitlementRequest.builder()
      .type(ENTITLE)
      .tenantId(TENANT_ID)
      .applications(List.of(APPLICATION_ID))
      .purgeOnRollback(purgeOnRollback)
      .build();
    var params = new HashMap<Object, Object>(moduleFlowParameters(request, MODULE, descriptor));
    params.put(PARAM_MODULE_DISCOVERY, MODULE_LOCATION);
    return params;
  }

  private static Map<String, String> stageParams() {
    return Map.of(PARAM_TENANT_NAME, TENANT_NAME);
  }
}
