package org.folio.entitlement.integration.okapi.flow;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_APP_DESCRIPTORS;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.integration.kafka.model.ModuleType.MODULE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_DEPRECATED_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_DEPRECATED_UI_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_MODULE_DESCRIPTOR_HOLDERS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_UI_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_UI_MODULE_DESCRIPTOR_HOLDERS;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestValues.module;
import static org.folio.entitlement.support.TestValues.moduleDescriptorHolder;
import static org.folio.entitlement.support.TestValues.modulesSequence;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.ModuleSequenceProvider;
import org.folio.entitlement.support.TestUtils;
import org.folio.flow.api.Flow;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OkapiModulesFlowProviderTest {

  private OkapiModulesFlowProvider okapiModulesFlowProvider;

  @Mock private ModuleSequenceProvider moduleSequenceProvider;
  @Mock private OkapiModulesRevokeFlowFactory revokeFlowFactory;
  @Mock private OkapiModulesEntitleFlowFactory entitleFlowFactory;
  @Mock private OkapiModulesUpgradeFlowFactory upgradeFlowFactory;

  @BeforeEach
  void setUp() {
    when(revokeFlowFactory.getEntitlementType()).thenReturn(REVOKE);
    when(entitleFlowFactory.getEntitlementType()).thenReturn(ENTITLE);
    when(upgradeFlowFactory.getEntitlementType()).thenReturn(UPGRADE);

    var factories = List.of(revokeFlowFactory, entitleFlowFactory, upgradeFlowFactory);
    okapiModulesFlowProvider = new OkapiModulesFlowProvider(moduleSequenceProvider, factories);
  }

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void createFlow_positive_entitleFlow() {
    var request = EntitlementRequest.builder().type(ENTITLE).ignoreErrors(true).build();
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_APP_DESCRIPTORS, applicationDescriptor());
    var context = StageContext.of(FLOW_STAGE_ID, flowParameters, emptyMap());
    var appContext = ApplicationStageContext.decorate(context);

    var beLayer = List.of(moduleDescriptorHolder(fooModuleDescriptor(), null));
    var uiLayer = List.of(moduleDescriptorHolder(uiFooModuleDescriptor(), null));
    var expectedFlowId = FLOW_STAGE_ID + "/OkapiModuleEntitleFlow";
    var expectedFlow = Flow.builder().id(expectedFlowId).executionStrategy(IGNORE_ON_ERROR).build();
    var expectedFlowParameters = Map.of(
      PARAM_MODULE_DESCRIPTORS, List.of(fooModuleDescriptor()),
      PARAM_UI_MODULE_DESCRIPTORS, List.of(uiFooModuleDescriptor()));

    when(moduleSequenceProvider.getSequence(appContext, MODULE)).thenReturn(modulesSequence(beLayer));
    when(moduleSequenceProvider.getSequence(appContext, UI_MODULE)).thenReturn(modulesSequence(uiLayer));
    when(entitleFlowFactory.createFlow(appContext, expectedFlowParameters)).thenReturn(expectedFlow);

    var result = okapiModulesFlowProvider.createFlow(context);

    assertThat(result).isEqualTo(expectedFlow);
  }

  @Test
  void createFlow_positive_revokeFlow() {
    var request = EntitlementRequest.builder().type(REVOKE).ignoreErrors(true).build();
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_APP_DESCRIPTORS, applicationDescriptor());

    var expectedFlowId = FLOW_STAGE_ID + "/OkapiModuleEntitleFlow";
    var expectedFlow = Flow.builder().id(expectedFlowId).executionStrategy(IGNORE_ON_ERROR).build();
    var expectedFlowParameters = Map.of(
      PARAM_MODULE_DESCRIPTORS, List.of(fooModuleDescriptor()),
      PARAM_UI_MODULE_DESCRIPTORS, List.of(uiFooModuleDescriptor()));

    var context = StageContext.of(FLOW_STAGE_ID, flowParameters, emptyMap());
    var appContext = ApplicationStageContext.decorate(context);
    var beLayer = List.of(moduleDescriptorHolder(fooModuleDescriptor(), null));
    var uiLayer = List.of(moduleDescriptorHolder(uiFooModuleDescriptor(), null));
    when(moduleSequenceProvider.getSequence(appContext, MODULE)).thenReturn(modulesSequence(beLayer));
    when(moduleSequenceProvider.getSequence(appContext, UI_MODULE)).thenReturn(modulesSequence(uiLayer));
    when(revokeFlowFactory.createFlow(appContext, expectedFlowParameters)).thenReturn(expectedFlow);

    var result = okapiModulesFlowProvider.createFlow(context);

    assertThat(result).isEqualTo(expectedFlow);
  }

  @Test
  void createFlow_positive_upgradeFlow() {
    var request = EntitlementRequest.builder().type(UPGRADE).ignoreErrors(true).build();
    var flowParameters = Map.of(
      PARAM_REQUEST, request,
      PARAM_APP_DESCRIPTORS, applicationDescriptorV2(),
      PARAM_ENTITLED_APPLICATION_DESCRIPTOR, applicationDescriptor());
    var context = StageContext.of(FLOW_STAGE_ID, flowParameters, emptyMap());
    var appContext = ApplicationStageContext.decorate(context);

    var expectedFlowId = FLOW_STAGE_ID + "/OkapiModuleEntitleFlow";
    var expectedFlow = Flow.builder().id(expectedFlowId).executionStrategy(IGNORE_ON_ERROR).build();

    var beLayer = List.of(moduleDescriptorHolder(fooModuleDescriptorV2(), fooModuleDescriptor()));
    var uiLayer = List.of(moduleDescriptorHolder(uiFooModuleDescriptorV2(), uiFooModuleDescriptor()));
    when(moduleSequenceProvider.getSequence(appContext, MODULE)).thenReturn(modulesSequence(beLayer));
    when(moduleSequenceProvider.getSequence(appContext, UI_MODULE)).thenReturn(modulesSequence(uiLayer));

    var expectedFlowParameters = Map.of(
      PARAM_MODULE_DESCRIPTOR_HOLDERS, beLayer,
      PARAM_UI_MODULE_DESCRIPTOR_HOLDERS, uiLayer,
      PARAM_DEPRECATED_MODULE_DESCRIPTORS, emptyList(),
      PARAM_DEPRECATED_UI_MODULE_DESCRIPTORS, emptyList());
    when(upgradeFlowFactory.createFlow(appContext, expectedFlowParameters)).thenReturn(expectedFlow);

    var result = okapiModulesFlowProvider.createFlow(context);

    assertThat(result).isEqualTo(expectedFlow);
  }

  private static ApplicationDescriptor applicationDescriptor() {
    return new ApplicationDescriptor().id("app-foo-1.0.0")
      .modules(List.of(module("mod-foo", "1.0.0")))
      .uiModules(List.of(module("folio_foo", "1.0.0")))
      .moduleDescriptors(List.of(fooModuleDescriptor()))
      .uiModuleDescriptors(List.of(uiFooModuleDescriptor()));
  }

  private static ApplicationDescriptor applicationDescriptorV2() {
    return new ApplicationDescriptor().id("app-foo-2.0.0")
      .modules(List.of(module("mod-foo", "2.0.0")))
      .uiModules(List.of(module("folio_foo", "2.0.0")))
      .moduleDescriptors(List.of(fooModuleDescriptorV2()))
      .uiModuleDescriptors(List.of(uiFooModuleDescriptorV2()));
  }

  private static ModuleDescriptor fooModuleDescriptor() {
    return new ModuleDescriptor().id("mod-foo-1.0.0");
  }

  private static ModuleDescriptor fooModuleDescriptorV2() {
    return new ModuleDescriptor().id("mod-foo-120.0");
  }

  private static ModuleDescriptor uiFooModuleDescriptor() {
    return new ModuleDescriptor().id("folio_foo-1.0.0");
  }

  private static ModuleDescriptor uiFooModuleDescriptorV2() {
    return new ModuleDescriptor().id("folio_foo-1.0.0");
  }
}
