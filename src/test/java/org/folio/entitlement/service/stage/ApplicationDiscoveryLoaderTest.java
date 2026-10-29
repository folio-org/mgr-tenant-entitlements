package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_MODULE_DISCOVERY_DATA;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.folio.entitlement.support.TestValues.module;
import static org.folio.entitlement.support.TestValues.moduleDiscoveries;
import static org.folio.entitlement.support.TestValues.simpleAppDescriptor;
import static org.folio.entitlement.support.TestValues.uiApplicationDescriptor;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.ResultList;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.support.TestValues;
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
    var stageContext = stageContext(appDescriptorWithProvides());

    when(applicationManagerService.getModuleDiscoveries(APPLICATION_ID, OKAPI_TOKEN)).thenReturn(moduleDiscoveries());

    applicationDiscoveryLoader.execute(stageContext);

    var moduleDiscoveryParam = stageContext.<Map<String, String>>get(PARAM_MODULE_DISCOVERY_DATA);
    assertThat(moduleDiscoveryParam).isEqualTo(Map.of("mod-bar-1.7.9", "http://mod-bar:8080"));
  }

  @Test
  void execute_positive_noDiscovery() {
    var stageContext = stageContext(uiApplicationDescriptor());
    applicationDiscoveryLoader.execute(stageContext);

    var moduleDiscoveryParam = stageContext.<Map<String, String>>get(PARAM_MODULE_DISCOVERY_DATA);
    assertThat(moduleDiscoveryParam).isNull();
  }

  @Test
  void execute_positive_moduleWithoutProvides_noDiscoveryRequested() {
    var stageContext = stageContext(simpleAppDescriptor(APPLICATION_ID));

    applicationDiscoveryLoader.execute(stageContext);

    var moduleDiscoveryParam = stageContext.<Map<String, String>>get(PARAM_MODULE_DISCOVERY_DATA);
    assertThat(moduleDiscoveryParam).isEmpty();
    verifyNoInteractions(applicationManagerService);
  }

  @Test
  void execute_positive_mixedModules_discoveryOnlyRequiredForModuleWithProvides() {
    var stageContext = stageContext(mixedAppDescriptor("mod-bar-1.7.9"));

    when(applicationManagerService.getModuleDiscoveries(APPLICATION_ID, OKAPI_TOKEN)).thenReturn(moduleDiscoveries());

    applicationDiscoveryLoader.execute(stageContext);

    var moduleDiscoveryParam = stageContext.<Map<String, String>>get(PARAM_MODULE_DISCOVERY_DATA);
    assertThat(moduleDiscoveryParam).isEqualTo(Map.of("mod-bar-1.7.9", "http://mod-bar:8080"));
  }

  @Test
  void execute_negative_moduleWithProvidesWithoutDiscovery_mixedModules() {
    var stageContext = stageContext(mixedAppDescriptor("mod-baz-3.0.0"));

    when(applicationManagerService.getModuleDiscoveries(APPLICATION_ID, OKAPI_TOKEN)).thenReturn(moduleDiscoveries());

    assertThatThrownBy(() -> applicationDiscoveryLoader.execute(stageContext))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Application discovery information is not defined for [mod-baz-3.0.0]");
  }

  @Test
  void execute_negative_moduleDiscoveriesNotFound() {
    var stageContext = stageContext(appDescriptorWithProvides());

    when(applicationManagerService.getModuleDiscoveries(APPLICATION_ID, OKAPI_TOKEN)).thenReturn(ResultList.empty());

    assertThatThrownBy(() -> applicationDiscoveryLoader.execute(stageContext))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Module discovery information is not found for application: " + APPLICATION_ID);
  }

  @Test
  void execute_negative_moduleDiscoveryInformationNotMatching() {
    var applicationDescriptor = TestValues.appDescriptor()
      .modules(List.of(module("mod-bar", "1.7.9"), module("mod-foo", "2.1.0")));
    var stageContext = stageContext(applicationDescriptor);

    when(applicationManagerService.getModuleDiscoveries(APPLICATION_ID, OKAPI_TOKEN)).thenReturn(moduleDiscoveries());

    assertThatThrownBy(() -> applicationDiscoveryLoader.execute(stageContext))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Application discovery information is not defined for [mod-foo-2.1.0]");
  }

  private static ApplicationDescriptor mixedAppDescriptor(String moduleWithProvidesId) {
    var withProvides = new ModuleDescriptor().id(moduleWithProvidesId)
      .addProvidesItem(new InterfaceDescriptor().id("bar").version("1.0"));
    var withoutProvides = new ModuleDescriptor().id("mod-edge-1.0.0");
    var appDescriptor = TestValues.appDescriptor();
    appDescriptor.setModules(List.of(
      module(moduleWithProvidesId.substring(0, moduleWithProvidesId.lastIndexOf('-')),
        moduleWithProvidesId.substring(moduleWithProvidesId.lastIndexOf('-') + 1)),
      module("mod-edge", "1.0.0")));
    appDescriptor.setModuleDescriptors(List.of(withProvides, withoutProvides));
    return appDescriptor;
  }

  private static ApplicationDescriptor appDescriptorWithProvides() {
    var appDescriptor = simpleAppDescriptor(APPLICATION_ID);
    appDescriptor.getModuleDescriptors().get(0).addProvidesItem(new InterfaceDescriptor().id("bar").version("1.0"));
    return appDescriptor;
  }

  private static ApplicationStageContext stageContext(ApplicationDescriptor applicationDescriptor) {
    var entitlementRequest = EntitlementRequest.builder()
      .type(ENTITLE).tenantId(TENANT_ID).okapiToken(OKAPI_TOKEN)
      .build();
    var contextParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParams = flowParameters(entitlementRequest, applicationDescriptor);
    return appStageContext(FLOW_STAGE_ID, flowParams, contextParameters);
  }
}
