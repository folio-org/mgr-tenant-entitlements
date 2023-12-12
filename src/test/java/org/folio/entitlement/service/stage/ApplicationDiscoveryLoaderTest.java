package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_DISCOVERY;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_DISCOVERY_DATA;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.module;
import static org.folio.entitlement.support.TestValues.moduleDiscoveries;
import static org.folio.entitlement.support.TestValues.simpleApplicationDescriptor;
import static org.folio.entitlement.support.TestValues.uiApplicationDescriptor;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.ResultList;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationDiscoveryLoaderTest {

  @InjectMocks private ApplicationDiscoveryLoader applicationDiscoveryLoader;
  @Mock private ApplicationManagerService applicationManagerService;

  @Test
  void execute_positive() {
    var stageContext = stageContext(simpleApplicationDescriptor(APPLICATION_ID));

    when(applicationManagerService.getModuleDiscoveries(APPLICATION_ID, OKAPI_TOKEN)).thenReturn(moduleDiscoveries());

    applicationDiscoveryLoader.execute(stageContext);

    var moduleDiscoveryParam = stageContext.<Map<String, String>>get(PARAM_MODULE_DISCOVERY_DATA);
    assertThat(moduleDiscoveryParam).isEqualTo(Map.of("mod-bar-1.7.9", "http://mod-bar:8080"));
  }

  @Test
  void execute_positive_noDiscovery() {
    var stageContext = stageContext(uiApplicationDescriptor());
    applicationDiscoveryLoader.execute(stageContext);

    var moduleDiscoveryParam = stageContext.<Map<String, String>>get(PARAM_MODULE_DISCOVERY);
    assertThat(moduleDiscoveryParam).isNull();
  }

  @Test
  void execute_negative_moduleDiscoveriesNotFound() {
    var stageContext = stageContext(simpleApplicationDescriptor(APPLICATION_ID));

    when(applicationManagerService.getModuleDiscoveries(APPLICATION_ID, OKAPI_TOKEN)).thenReturn(ResultList.empty());

    assertThatThrownBy(() -> applicationDiscoveryLoader.execute(stageContext))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Module discovery information is not found");
  }

  @Test
  void execute_negative_moduleDiscoveryInformationNotMatching() {
    var applicationDescriptor = new ApplicationDescriptor().id("test-application")
      .modules(List.of(module("mod-bar", "1.7.9"), module("mod-foo", "2.1.0")));
    var stageContext = stageContext(applicationDescriptor);

    when(applicationManagerService.getModuleDiscoveries(APPLICATION_ID, OKAPI_TOKEN)).thenReturn(moduleDiscoveries());

    assertThatThrownBy(() -> applicationDiscoveryLoader.execute(stageContext))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Application discovery information is not defined for [mod-foo-2.1.0]");
  }

  private StageContext stageContext(ApplicationDescriptor applicationDescriptor) {
    var entitlementRequest = EntitlementRequest.builder().tenantId(TENANT_ID).okapiToken(OKAPI_TOKEN).build();
    var contextParameters = Map.of(PARAM_APP_DESCRIPTOR, applicationDescriptor);
    var flowParams = Map.of(PARAM_APP_ID, APPLICATION_ID, PARAM_REQUEST, entitlementRequest);
    return StageContext.of(FLOW_STAGE_ID, flowParams, contextParameters);
  }
}
