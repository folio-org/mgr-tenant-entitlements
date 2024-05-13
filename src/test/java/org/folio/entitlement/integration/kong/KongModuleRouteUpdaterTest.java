package org.folio.entitlement.integration.kong;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_INSTALLED_MODULE_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.moduleFlowParameters;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

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
class KongModuleRouteUpdaterTest {

  @InjectMocks private KongModuleRouteUpdater kongModuleRouteUpdater;
  @Mock private KongGatewayService kongGatewayService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_newModule() {
    var moduleDescriptor = moduleDescriptor("mod-foo-1.0.0");
    var flowParameters = moduleFlowParameters(entitlementRequest(), moduleDescriptor);
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    kongModuleRouteUpdater.execute(stageContext);

    verify(kongGatewayService).addRoutes(TENANT_NAME, List.of(moduleDescriptor));
  }

  @Test
  void execute_positive_updatedModule() {
    var installedModuleDescriptor = moduleDescriptor("mod-foo-1.0.0");
    var moduleDescriptor = moduleDescriptor("mod-foo-2.0.0");
    var flowParameters = moduleFlowParameters(entitlementRequest(), moduleDescriptor, installedModuleDescriptor);
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    kongModuleRouteUpdater.execute(stageContext);

    verify(kongGatewayService).addRoutes(TENANT_NAME, List.of(moduleDescriptor));
    verify(kongGatewayService).removeRoutes(TENANT_NAME, List.of(installedModuleDescriptor));
  }

  @Test
  void execute_positive_updatedModuleNotChangedVersion() {
    var installedModuleDescriptor = moduleDescriptor("mod-foo-1.0.0");
    var moduleDescriptor = moduleDescriptor("mod-foo-1.0.0");
    var flowParameters = moduleFlowParameters(entitlementRequest(), moduleDescriptor, installedModuleDescriptor);
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    kongModuleRouteUpdater.execute(stageContext);

    verifyNoInteractions(kongGatewayService);
  }

  @Test
  void execute_positive_deprecatedModule() {
    var installedModuleDescriptor = moduleDescriptor("mod-foo-1.0.0");
    var flowParameters = Map.of(
      PARAM_REQUEST, entitlementRequest(),
      PARAM_MODULE_ID, installedModuleDescriptor.getId(),
      PARAM_INSTALLED_MODULE_DESCRIPTOR, installedModuleDescriptor,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
    var stageData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageData);

    kongModuleRouteUpdater.execute(stageContext);

    verify(kongGatewayService).removeRoutes(TENANT_NAME, List.of(installedModuleDescriptor));
  }

  @Test
  void getStageName_positive() {
    var flowParameters = moduleFlowParameters(entitlementRequest(), moduleDescriptor("mod-foo-1.0.0"));
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var result = kongModuleRouteUpdater.getStageName(stageContext);
    assertThat(result).isEqualTo("mod-foo-1.0.0-kongModuleRouteUpdater");
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder()
      .type(UPGRADE)
      .applications(List.of(APPLICATION_ID))
      .tenantId(TENANT_ID)
      .build();
  }

  private static ModuleDescriptor moduleDescriptor(String id) {
    return new ModuleDescriptor().id(id);
  }
}
