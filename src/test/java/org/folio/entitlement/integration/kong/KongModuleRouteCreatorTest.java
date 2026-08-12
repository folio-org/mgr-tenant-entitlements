package org.folio.entitlement.integration.kong;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
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
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
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
class KongModuleRouteCreatorTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String MODULE_LOCATION = "http://mod-foo:8080";

  @Mock private KongGatewayService kongGatewayService;
  @Mock private ThreadLocalModuleStageContext threadLocalModuleStageContext;

  private KongModuleRouteCreator kongModuleRouteCreator;

  @BeforeEach
  void setUp() {
    kongModuleRouteCreator = new KongModuleRouteCreator(kongGatewayService, defaultProperties());
    kongModuleRouteCreator.setThreadLocalModuleStageContext(threadLocalModuleStageContext);
  }

  @AfterEach
  void tearDown() {
    reset(threadLocalModuleStageContext);
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_routeManagementEnabled_tenantChecksDisabled() {
    var descriptor = moduleDescriptor();
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(descriptor), stageParams());

    kongModuleRouteCreator.execute(stageContext);

    verify(kongGatewayService).upsertService(new Service().name(MODULE_ID).url(MODULE_LOCATION));
    verify(kongGatewayService).addRoutes(List.of(descriptor));
  }

  @Test
  void execute_positive_tenantChecksEnabled() {
    var creator = new KongModuleRouteCreator(kongGatewayService, propertiesWithTenantChecks());
    creator.setThreadLocalModuleStageContext(threadLocalModuleStageContext);

    var descriptor = moduleDescriptor();
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(descriptor), stageParams());

    creator.execute(stageContext);

    verify(kongGatewayService).upsertService(new Service().name(MODULE_ID).url(MODULE_LOCATION));
    verify(kongGatewayService).addRoutes(List.of(descriptor));
    verify(kongGatewayService).addTenantToModuleRoutes(MODULE_ID, TENANT_NAME);
  }

  @Test
  void execute_positive_routeManagementDisabled() {
    var creator = new KongModuleRouteCreator(kongGatewayService, propertiesWithoutRouteManagement());
    creator.setThreadLocalModuleStageContext(threadLocalModuleStageContext);

    var descriptor = moduleDescriptor();
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(descriptor), stageParams());

    creator.execute(stageContext);

    verify(kongGatewayService).upsertService(new Service().name(MODULE_ID).url(MODULE_LOCATION));
  }

  @Test
  void execute_positive_uiModule() {
    var descriptor = moduleDescriptor();
    var flowParams = moduleFlowParameters(entitlementRequest(), UI_MODULE, descriptor);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParams, stageParams());

    kongModuleRouteCreator.execute(stageContext);

    verifyNoInteractions(kongGatewayService);
  }

  @Test
  void cancel_positive_routeManagementEnabled() {
    var descriptor = moduleDescriptor();
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(descriptor), stageParams());

    kongModuleRouteCreator.cancel(stageContext);

    verify(kongGatewayService).deleteServiceRoutes(MODULE_ID);
    verify(kongGatewayService).deleteService(MODULE_ID);
  }

  @Test
  void cancel_positive_routeManagementDisabled() {
    var creator = new KongModuleRouteCreator(kongGatewayService, propertiesWithoutRouteManagement());
    creator.setThreadLocalModuleStageContext(threadLocalModuleStageContext);

    var descriptor = moduleDescriptor();
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(descriptor), stageParams());

    creator.cancel(stageContext);

    verify(kongGatewayService).deleteService(MODULE_ID);
  }

  @Test
  void cancel_positive_deleteServiceRoutesThrowsNoSuchElement() {
    var descriptor = moduleDescriptor();
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(descriptor), stageParams());

    org.mockito.Mockito.doThrow(new NoSuchElementException())
      .when(kongGatewayService).deleteServiceRoutes(MODULE_ID);

    kongModuleRouteCreator.cancel(stageContext);

    verify(kongGatewayService).deleteServiceRoutes(MODULE_ID);
    verify(kongGatewayService).deleteService(MODULE_ID);
  }

  @Test
  void cancel_positive_uiModule() {
    var descriptor = moduleDescriptor();
    var flowParams = moduleFlowParameters(entitlementRequest(), UI_MODULE, descriptor);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParams, stageParams());

    kongModuleRouteCreator.cancel(stageContext);

    verifyNoInteractions(kongGatewayService);
  }

  @Test
  void shouldCancelIfFailed_positive() {
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(moduleDescriptor()), stageParams());

    assertThat(kongModuleRouteCreator.shouldCancelIfFailed(stageContext)).isTrue();
  }

  @Test
  void getStageName_positive() {
    var stageContext = moduleStageContext(FLOW_STAGE_ID, moduleFlowWithDiscovery(moduleDescriptor()), emptyMap());

    assertThat(kongModuleRouteCreator.getStageName(stageContext)).isEqualTo(MODULE_ID + "-kongModuleRouteCreator");
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
    var params = new HashMap<Object, Object>(moduleFlowParameters(entitlementRequest(), MODULE, descriptor));
    params.put(PARAM_MODULE_DISCOVERY, MODULE_LOCATION);
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
