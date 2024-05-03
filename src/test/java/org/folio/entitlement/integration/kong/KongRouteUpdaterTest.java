package org.folio.entitlement.integration.kong;

import static java.util.Collections.emptyList;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_DEPRECATED_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_MODULE_DESCRIPTOR_HOLDERS;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.moduleDescriptorHolder;
import static org.folio.entitlement.support.TestValues.okapiStageContext;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.folio.tools.kong.service.KongGatewayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KongRouteUpdaterTest {

  @InjectMocks private KongRouteUpdater kongRouteUpdater;
  @Mock private KongGatewayService kongGatewayService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_modulesChanged() {
    var modFooV1 = moduleDescriptor("mod-foo", "1.0.0");
    var modFooV2 = moduleDescriptor("mod-foo", "2.0.0");
    var modBar = moduleDescriptor("mod-bar", "1.0.0");
    var modBaz = moduleDescriptor("mod-baz", "1.0.0");
    var modTest = moduleDescriptor("mod-test", "1.0.0");

    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build();
    var moduleDescriptorHolders = List.of(
      moduleDescriptorHolder(modFooV2, modFooV1),
      moduleDescriptorHolder(modBar, modBar),
      moduleDescriptorHolder(modBaz, null));

    var flowParameters = Map.of(
      PARAM_REQUEST, request,
      PARAM_MODULE_DESCRIPTOR_HOLDERS, moduleDescriptorHolders,
      PARAM_DEPRECATED_MODULE_DESCRIPTORS, List.of(modTest));

    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = okapiStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    kongRouteUpdater.execute(stageContext);

    verify(kongGatewayService).updateRoutes(TENANT_NAME, List.of(modFooV2));
    verify(kongGatewayService).removeRoutes(TENANT_NAME, List.of(modFooV1));
    verify(kongGatewayService).updateRoutes(TENANT_NAME, List.of(modBaz));
    verify(kongGatewayService).removeRoutes(TENANT_NAME, List.of(modTest));
  }

  @Test
  void execute_positive_updatedModule() {
    var modFooV1 = moduleDescriptor("mod-foo", "1.0.0");
    var modFooV2 = moduleDescriptor("mod-foo", "2.0.0");

    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build();
    var moduleDescriptorHolders = List.of(moduleDescriptorHolder(modFooV2, modFooV1));
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_MODULE_DESCRIPTOR_HOLDERS, moduleDescriptorHolders);
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = okapiStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    kongRouteUpdater.execute(stageContext);

    verify(kongGatewayService).updateRoutes(TENANT_NAME, List.of(modFooV2));
    verify(kongGatewayService).removeRoutes(TENANT_NAME, List.of(modFooV1));
    verify(kongGatewayService).removeRoutes(TENANT_NAME, emptyList());
  }

  @Test
  void execute_positive_deprecatedModule() {
    var modFooV1 = moduleDescriptor("mod-foo", "1.0.0");
    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(UPGRADE).build();
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_DEPRECATED_MODULE_DESCRIPTORS, List.of(modFooV1));
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = okapiStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    kongRouteUpdater.execute(stageContext);

    verify(kongGatewayService).removeRoutes(TENANT_NAME, List.of(modFooV1));
  }

  private static ModuleDescriptor moduleDescriptor(String name, String version) {
    return new ModuleDescriptor().id(name + "-" + version);
  }
}
