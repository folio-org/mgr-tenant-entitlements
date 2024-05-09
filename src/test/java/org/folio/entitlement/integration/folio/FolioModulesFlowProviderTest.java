package org.folio.entitlement.integration.folio;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_MODULE_DISCOVERY_DATA;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_INSTALLED_MODULE_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_DISCOVERY;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_ID;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_TYPE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.MODULE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_VERSION;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.folio.entitlement.support.TestValues.module;
import static org.folio.entitlement.support.TestValues.moduleDescriptorHolder;
import static org.folio.entitlement.support.TestValues.modulesSequence;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.am.model.Module;
import org.folio.entitlement.integration.folio.flow.FolioModuleEntitleFlowFactory;
import org.folio.entitlement.integration.folio.flow.FolioModuleRevokeFlowFactory;
import org.folio.entitlement.integration.folio.flow.FolioModuleUpgradeFlowFactory;
import org.folio.entitlement.integration.folio.flow.FolioModulesFlowProvider;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.service.ModuleSequenceProvider;
import org.folio.entitlement.support.TestUtils;
import org.folio.flow.api.Flow;
import org.folio.flow.api.FlowEngine;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FolioModulesFlowProviderTest {

  private static final String MOD_FOO_ID = "mod-foo-1.0.0";
  private static final String MOD_FOO_V2_ID = "mod-foo-2.0.0";
  private static final String MOD_BAR_ID = "mod-bar-1.0.0";
  private static final String UI_FOO_ID = "folio_foo-1.0.0";
  private static final String UI_BAR_ID = "folio_bar-2.0.0";

  private FolioModulesFlowProvider flowProvider;
  @Mock private FolioModuleRevokeFlowFactory moduleRevokeFlowFactory;
  @Mock private ModuleSequenceProvider moduleSequenceProvider;
  @Mock private FolioModuleEntitleFlowFactory moduleEntitleFlowFactory;
  @Mock private FolioModuleUpgradeFlowFactory moduleUpgradeFlowFactory;
  @Mock private Stage<StageContext> moduleStage;

  private final FlowEngine flowEngine = singleThreadFlowEngine("folio-module-installer-flow-engine", false);

  @BeforeEach
  void setUp() {
    when(moduleEntitleFlowFactory.getEntitlementType()).thenReturn(ENTITLE);
    when(moduleRevokeFlowFactory.getEntitlementType()).thenReturn(REVOKE);
    when(moduleUpgradeFlowFactory.getEntitlementType()).thenReturn(UPGRADE);
    var moduleFlowFactories = List.of(moduleEntitleFlowFactory, moduleRevokeFlowFactory, moduleUpgradeFlowFactory);
    flowProvider = new FolioModulesFlowProvider(moduleFlowFactories, moduleSequenceProvider);
  }

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void createFlow_positive_entitleRequestSingleModule() {
    var request = EntitlementRequest.builder().type(ENTITLE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(List.of(fooModuleDesc()), emptyList());

    var flowParameters = flowParameters(request, applicationDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, Map.of(MOD_FOO_ID, "https://mod-foo:8443"));
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    mockStageNames(moduleStage);
    when(moduleEntitleFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);
    var layer = List.of(moduleDescriptorHolder(fooModuleDesc(), null));
    when(moduleSequenceProvider.getSequence(stageContext, MODULE)).thenReturn(modulesSequence(layer));
    when(moduleSequenceProvider.getSequence(stageContext, UI_MODULE)).thenReturn(modulesSequence());

    var flow = flowProvider.createFlow(stageContext);
    flowEngine.execute(flow);

    var fooFlowId = getEntitleStageFlowId(ENTITLE, 0, MOD_FOO_ID);
    var fooFlowParams = fooFlowParams();
    var inOrder = inOrder(moduleEntitleFlowFactory, moduleStage, moduleSequenceProvider);
    inOrder.verify(moduleSequenceProvider).getSequence(stageContext, MODULE);
    inOrder.verify(moduleEntitleFlowFactory).createModuleFlow(fooFlowId, IGNORE_ON_ERROR, fooFlowParams);
    inOrder.verify(moduleSequenceProvider).getSequence(stageContext, UI_MODULE);
    inOrder.verify(moduleStage).execute(StageContext.of(fooFlowId, fooFlowParams, emptyMap()));
  }

  @Test
  void createFlow_positive_revokeRequestSingleModule() {
    var request = EntitlementRequest.builder().type(REVOKE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(List.of(fooModuleDesc()), emptyList());

    var flowParameters = flowParameters(request, applicationDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, Map.of(MOD_FOO_ID, "https://mod-foo:8443"));
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var layer = List.of(moduleDescriptorHolder(fooModuleDesc(), null));
    when(moduleSequenceProvider.getSequence(stageContext, MODULE)).thenReturn(modulesSequence(layer));
    when(moduleSequenceProvider.getSequence(stageContext, UI_MODULE)).thenReturn(modulesSequence());

    mockStageNames(moduleStage);
    when(moduleRevokeFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);

    var flow = flowProvider.createFlow(stageContext);
    flowEngine.execute(flow);

    var fooFlowId = getEntitleStageFlowId(REVOKE, 0, MOD_FOO_ID);
    var fooFlowParams = fooFlowParams();
    var inOrder = inOrder(moduleRevokeFlowFactory, moduleStage, moduleSequenceProvider);
    inOrder.verify(moduleSequenceProvider).getSequence(stageContext, MODULE);
    inOrder.verify(moduleRevokeFlowFactory).createModuleFlow(fooFlowId, IGNORE_ON_ERROR, fooFlowParams);
    inOrder.verify(moduleSequenceProvider).getSequence(stageContext, UI_MODULE);
    inOrder.verify(moduleStage).execute(StageContext.of(fooFlowId, fooFlowParams, emptyMap()));
  }

  @Test
  void createFlow_positive_upgradeRequestSingleModule() {
    mockStageNames(moduleStage);
    when(moduleUpgradeFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);

    var request = EntitlementRequest.builder().type(UPGRADE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(List.of(fooModuleDescV2()), emptyList());
    var entitledAppDescriptor = appDescriptor(List.of(fooModuleDesc()), emptyList());
    var flowParameters = flowParameters(request, applicationDescriptor, entitledAppDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, Map.of(MOD_FOO_V2_ID, "https://mod-foo-v2:8443"));
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var layer = List.of(moduleDescriptorHolder(fooModuleDescV2(), fooModuleDesc()));
    when(moduleSequenceProvider.getSequence(stageContext, MODULE)).thenReturn(modulesSequence(layer));
    when(moduleSequenceProvider.getSequence(stageContext, UI_MODULE)).thenReturn(modulesSequence());

    var flow = flowProvider.createFlow(stageContext);
    flowEngine.execute(flow);

    var fooFlowId = getEntitleStageFlowId(UPGRADE, 0, MOD_FOO_V2_ID);
    var fooFlowParameters = upgradeModFooV2Params();

    var inOrder = inOrder(moduleUpgradeFlowFactory, moduleStage, moduleSequenceProvider);
    inOrder.verify(moduleSequenceProvider).getSequence(stageContext, MODULE);
    inOrder.verify(moduleUpgradeFlowFactory).createModuleFlow(fooFlowId, IGNORE_ON_ERROR, fooFlowParameters);
    inOrder.verify(moduleSequenceProvider).getSequence(stageContext, UI_MODULE);
    inOrder.verify(moduleStage).execute(StageContext.of(fooFlowId, fooFlowParameters, emptyMap()));
  }

  @Test
  void createFlow_positive_upgradeRequestNewModules() {
    mockStageNames(moduleStage);
    when(moduleUpgradeFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);
    when(moduleUpgradeFlowFactory.createUiModuleFlow(any(), any(), any())).then(this::createFlow);

    var request = EntitlementRequest.builder().type(UPGRADE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(List.of(fooModuleDesc()), List.of(uiFooModuleDesc()));
    var entitledAppDescriptor = appDescriptor(emptyList(), emptyList());
    var flowParameters = flowParameters(request, applicationDescriptor, entitledAppDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, moduleDiscoveryTable());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var layer = List.of(moduleDescriptorHolder(fooModuleDesc(), null));
    var uiLayer = List.of(moduleDescriptorHolder(uiFooModuleDesc(), null));
    when(moduleSequenceProvider.getSequence(stageContext, MODULE)).thenReturn(modulesSequence(layer));
    when(moduleSequenceProvider.getSequence(stageContext, UI_MODULE)).thenReturn(modulesSequence(uiLayer));

    var flow = flowProvider.createFlow(stageContext);
    flowEngine.execute(flow);

    var fooFlowId = getEntitleStageFlowId(UPGRADE, 0, MOD_FOO_ID);
    var fooFlowParameters = Map.of(
      PARAM_MODULE_TYPE, MODULE,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID,
      PARAM_MODULE_ID, MOD_FOO_ID,
      PARAM_MODULE_DESCRIPTOR, fooModuleDesc(),
      PARAM_MODULE_DISCOVERY, "https://mod-foo:8443");

    var uiFlowId = getEntitleStageFlowId(UPGRADE, 1, UI_FOO_ID);
    var uiFooFlowParameters = Map.of(
      PARAM_MODULE_TYPE, UI_MODULE,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID,
      PARAM_MODULE_ID, UI_FOO_ID,
      PARAM_MODULE_DESCRIPTOR, uiFooModuleDesc());

    var inOrder = inOrder(moduleUpgradeFlowFactory, moduleStage, moduleSequenceProvider);
    inOrder.verify(moduleSequenceProvider).getSequence(stageContext, MODULE);
    inOrder.verify(moduleUpgradeFlowFactory).createModuleFlow(fooFlowId, IGNORE_ON_ERROR, fooFlowParameters);
    inOrder.verify(moduleSequenceProvider).getSequence(stageContext, UI_MODULE);
    inOrder.verify(moduleUpgradeFlowFactory).createUiModuleFlow(uiFlowId, IGNORE_ON_ERROR, uiFooFlowParameters);
    inOrder.verify(moduleStage).execute(StageContext.of(fooFlowId, fooFlowParameters, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(uiFlowId, uiFooFlowParameters, emptyMap()));
  }

  @Test
  void createFlow_positive_upgradeRequestDeprecatedModule() {
    mockStageNames(moduleStage);
    when(moduleUpgradeFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);
    when(moduleUpgradeFlowFactory.createUiModuleFlow(any(), any(), any())).then(this::createFlow);

    var request = EntitlementRequest.builder().type(UPGRADE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(emptyList(), emptyList());
    var entitledAppDescriptor = appDescriptor(List.of(fooModuleDesc()), List.of(uiFooModuleDesc()));
    var flowParameters = flowParameters(request, applicationDescriptor, entitledAppDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, moduleDiscoveryTable());
    var context = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var beLayers = List.of(List.of(fooModuleDesc()));
    var uiLayers = List.of(List.of(uiFooModuleDesc()));
    when(moduleSequenceProvider.getSequence(context, MODULE)).thenReturn(modulesSequence(emptyList(), beLayers));
    when(moduleSequenceProvider.getSequence(context, UI_MODULE)).thenReturn(modulesSequence(emptyList(), uiLayers));

    var flow = flowProvider.createFlow(context);
    flowEngine.execute(flow);

    var fooFlowId = getDeprecatedStageFlowId(0, MOD_FOO_ID);
    var fooFlowParameters = Map.of(
      PARAM_MODULE_TYPE, MODULE,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID,
      PARAM_MODULE_ID, MOD_FOO_ID,
      PARAM_INSTALLED_MODULE_DESCRIPTOR, fooModuleDesc(),
      PARAM_MODULE_DISCOVERY, "https://mod-foo:8443");

    var uiFooFlowId = getDeprecatedStageFlowId(1, UI_FOO_ID);
    var uiFlowParameters = deprecatedUiFooParams();

    var inOrder = inOrder(moduleUpgradeFlowFactory, moduleStage, moduleSequenceProvider);
    inOrder.verify(moduleSequenceProvider).getSequence(context, MODULE);
    inOrder.verify(moduleSequenceProvider).getSequence(context, UI_MODULE);
    inOrder.verify(moduleUpgradeFlowFactory).createModuleFlow(fooFlowId, IGNORE_ON_ERROR, fooFlowParameters);
    inOrder.verify(moduleUpgradeFlowFactory).createUiModuleFlow(uiFooFlowId, IGNORE_ON_ERROR, uiFlowParameters);
    inOrder.verify(moduleStage).execute(StageContext.of(fooFlowId, fooFlowParameters, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(uiFooFlowId, uiFlowParameters, emptyMap()));
  }

  @Test
  void createFlow_positive_complexEntitleRequest() {
    mockStageNames(moduleStage);
    when(moduleEntitleFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);
    when(moduleEntitleFlowFactory.createUiModuleFlow(any(), any(), any())).then(this::createFlow);

    var request = EntitlementRequest.builder().type(ENTITLE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(List.of(fooModuleDesc(), barModuleDesc()), List.of(uiFooModuleDesc()));
    var flowParameters = flowParameters(request, applicationDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, moduleDiscoveryTable());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var layer1 = List.of(moduleDescriptorHolder(fooModuleDesc(), null));
    var layer2 = List.of(moduleDescriptorHolder(barModuleDesc(), null));
    var uiLayer = List.of(moduleDescriptorHolder(uiFooModuleDesc(), null));
    when(moduleSequenceProvider.getSequence(stageContext, MODULE)).thenReturn(modulesSequence(layer1, layer2));
    when(moduleSequenceProvider.getSequence(stageContext, UI_MODULE)).thenReturn(modulesSequence(uiLayer));

    var flow = flowProvider.createFlow(stageContext);
    flowEngine.execute(flow);

    assertThat(flow).isNotNull();

    var inOrder = inOrder(moduleEntitleFlowFactory, moduleStage, moduleSequenceProvider);
    inOrder.verify(moduleSequenceProvider).getSequence(stageContext, MODULE);

    var fooFlowId = getEntitleStageFlowId(ENTITLE, 0, MOD_FOO_ID);
    var fooFlowParams = fooFlowParams();
    inOrder.verify(moduleEntitleFlowFactory).createModuleFlow(fooFlowId, IGNORE_ON_ERROR, fooFlowParams);

    var barFlowId = getEntitleStageFlowId(ENTITLE, 1, MOD_BAR_ID);
    var barFlowParams = barFlowParams();
    inOrder.verify(moduleEntitleFlowFactory).createModuleFlow(barFlowId, IGNORE_ON_ERROR, barFlowParams);

    inOrder.verify(moduleSequenceProvider).getSequence(stageContext, UI_MODULE);
    var uiFooFlowId = getEntitleStageFlowId(ENTITLE, 2, UI_FOO_ID);
    var uiFooFlowParams = uiFooFlowParams();
    inOrder.verify(moduleEntitleFlowFactory).createUiModuleFlow(uiFooFlowId, IGNORE_ON_ERROR, uiFooFlowParams);

    inOrder.verify(moduleStage).execute(StageContext.of(fooFlowId, fooFlowParams, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(barFlowId, barFlowParams, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(uiFooFlowId, uiFooFlowParams, emptyMap()));
  }

  @Test
  void createFlow_positive_complexRevokeRequest() {
    mockStageNames(moduleStage);
    when(moduleRevokeFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);
    when(moduleRevokeFlowFactory.createUiModuleFlow(any(), any(), any())).then(this::createFlow);

    var request = EntitlementRequest.builder().type(REVOKE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(List.of(fooModuleDesc(), barModuleDesc()), List.of(uiFooModuleDesc()));
    var flowParameters = flowParameters(request, applicationDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, moduleDiscoveryTable());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var layer1 = List.of(moduleDescriptorHolder(barModuleDesc(), null));
    var layer2 = List.of(moduleDescriptorHolder(fooModuleDesc(), null));
    var uiLayer = List.of(moduleDescriptorHolder(uiFooModuleDesc(), null));
    when(moduleSequenceProvider.getSequence(stageContext, MODULE)).thenReturn(modulesSequence(layer1, layer2));
    when(moduleSequenceProvider.getSequence(stageContext, UI_MODULE)).thenReturn(modulesSequence(uiLayer));

    var flow = flowProvider.createFlow(stageContext);
    flowEngine.execute(flow);

    assertThat(flow).isNotNull();

    var inOrder = inOrder(moduleRevokeFlowFactory, moduleStage, moduleSequenceProvider);
    inOrder.verify(moduleSequenceProvider).getSequence(stageContext, MODULE);

    var barFlowId = getEntitleStageFlowId(REVOKE, 0, MOD_BAR_ID);
    var barFlowParams = barFlowParams();
    inOrder.verify(moduleRevokeFlowFactory).createModuleFlow(barFlowId, IGNORE_ON_ERROR, barFlowParams);

    var fooFlowId = getEntitleStageFlowId(REVOKE, 1, MOD_FOO_ID);
    var fooFlowParams = fooFlowParams();
    inOrder.verify(moduleRevokeFlowFactory).createModuleFlow(fooFlowId, IGNORE_ON_ERROR, fooFlowParams);

    inOrder.verify(moduleSequenceProvider).getSequence(stageContext, UI_MODULE);
    var uiFooFlowId = getEntitleStageFlowId(REVOKE, 2, UI_FOO_ID);
    var uiFooFlowParams = uiFooFlowParams();
    inOrder.verify(moduleRevokeFlowFactory).createUiModuleFlow(uiFooFlowId, IGNORE_ON_ERROR, uiFooFlowParams);

    inOrder.verify(moduleStage).execute(StageContext.of(barFlowId, barFlowParams, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(fooFlowId, fooFlowParams, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(uiFooFlowId, uiFooFlowParams, emptyMap()));
  }

  @Test
  void createFlow_positive_complexUpgradeRequest() {
    mockStageNames(moduleStage);
    when(moduleUpgradeFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);
    when(moduleUpgradeFlowFactory.createUiModuleFlow(any(), any(), any())).then(this::createFlow);

    var request = EntitlementRequest.builder().type(UPGRADE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(List.of(fooModuleDescV2(), barModuleDesc()), List.of(uiBarModuleDesc()));
    var entitledAppDescriptor = appDescriptor(List.of(fooModuleDesc(), barModuleDesc()), List.of(uiFooModuleDesc()));
    var flowParameters = flowParameters(request, applicationDescriptor, entitledAppDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, moduleDiscoveryTable());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var layer1 = List.of(moduleDescriptorHolder(fooModuleDescV2(), fooModuleDesc()));
    var layer2 = List.of(moduleDescriptorHolder(barModuleDesc(), barModuleDesc()));
    var uiLayer = List.of(moduleDescriptorHolder(uiBarModuleDesc(), null));
    var uiModuleSequence = modulesSequence(List.of(uiLayer), List.of(List.of(uiFooModuleDesc())));
    when(moduleSequenceProvider.getSequence(stageContext, MODULE)).thenReturn(modulesSequence(layer1, layer2));
    when(moduleSequenceProvider.getSequence(stageContext, UI_MODULE)).thenReturn(uiModuleSequence);

    var flow = flowProvider.createFlow(stageContext);
    flowEngine.execute(flow);

    assertThat(flow).isNotNull();

    var inOrder = inOrder(moduleUpgradeFlowFactory, moduleStage, moduleSequenceProvider);
    inOrder.verify(moduleSequenceProvider).getSequence(stageContext, MODULE);

    var fooV2FlowId = getEntitleStageFlowId(UPGRADE, 0, MOD_FOO_V2_ID);
    var fooV2FlowParams = upgradeModFooV2Params();
    inOrder.verify(moduleUpgradeFlowFactory).createModuleFlow(fooV2FlowId, IGNORE_ON_ERROR, fooV2FlowParams);

    var barFlowId = getEntitleStageFlowId(UPGRADE, 1, MOD_BAR_ID);
    var barFlowParams = upgradeModBarParams();
    inOrder.verify(moduleUpgradeFlowFactory).createModuleFlow(barFlowId, IGNORE_ON_ERROR, barFlowParams);

    inOrder.verify(moduleSequenceProvider).getSequence(stageContext, UI_MODULE);
    var uiBarFlowId = getEntitleStageFlowId(UPGRADE, 2, UI_BAR_ID);
    var uiBarFlowParams = uiBarFlowParams();
    inOrder.verify(moduleUpgradeFlowFactory).createUiModuleFlow(uiBarFlowId, IGNORE_ON_ERROR, uiBarFlowParams);

    var uiFooFlowId = getDeprecatedStageFlowId(0, UI_FOO_ID);
    var uiFooFlowParams = deprecatedUiFooParams();
    inOrder.verify(moduleUpgradeFlowFactory).createUiModuleFlow(uiFooFlowId, IGNORE_ON_ERROR, uiFooFlowParams);

    inOrder.verify(moduleStage).execute(StageContext.of(fooV2FlowId, fooV2FlowParams, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(barFlowId, barFlowParams, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(uiBarFlowId, uiBarFlowParams, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(uiFooFlowId, uiFooFlowParams, emptyMap()));
  }

  private Flow createFlow(InvocationOnMock inv) {
    return Flow.builder()
      .id(inv.getArgument(0))
      .stage(moduleStage)
      .flowParameters(inv.getArgument(2))
      .executionStrategy(inv.getArgument(1))
      .build();
  }

  private static ApplicationDescriptor appDescriptor(
    List<ModuleDescriptor> moduleDescriptors, List<ModuleDescriptor> uiModuleDescriptors) {
    return new ApplicationDescriptor()
      .id(APPLICATION_ID)
      .name(APPLICATION_NAME)
      .version(APPLICATION_VERSION)
      .modules(mapItems(moduleDescriptors, FolioModulesFlowProviderTest::toModule))
      .uiModules(mapItems(uiModuleDescriptors, FolioModulesFlowProviderTest::toModule))
      .moduleDescriptors(moduleDescriptors)
      .uiModuleDescriptors(uiModuleDescriptors);
  }

  private static ModuleDescriptor fooModuleDesc() {
    return new ModuleDescriptor()
      .id(MOD_FOO_ID)
      .name("Foo module")
      .provides(List.of(new InterfaceDescriptor().id("foo-api").version("1.0").handlers(List.of(
        new RoutingEntry().methods(List.of("GET")).pathPattern("/foo-api/items")))));
  }

  private static ModuleDescriptor uiFooModuleDesc() {
    return new ModuleDescriptor()
      .id(UI_FOO_ID)
      .name("UI foo module")
      .requires(List.of(new InterfaceReference().id("foo-api").version("1.0")));
  }

  private static ModuleDescriptor uiBarModuleDesc() {
    return new ModuleDescriptor()
      .id(UI_BAR_ID)
      .name("UI bar module")
      .requires(List.of(new InterfaceReference().id("foo-api").version("2.0")));
  }

  private static ModuleDescriptor fooModuleDescV2() {
    return new ModuleDescriptor()
      .id(MOD_FOO_V2_ID)
      .name("Foo module name")
      .provides(List.of(new InterfaceDescriptor().id("foo-api").version("2.0").handlers(List.of(
        new RoutingEntry().methods(List.of("GET")).pathPattern("/foo-api/v2/items")))));
  }

  private static ModuleDescriptor barModuleDesc() {
    return new ModuleDescriptor()
      .id(MOD_BAR_ID)
      .name("Bar module")
      .requires(List.of(new InterfaceReference().id("foo-api").version("1.0 2.0")))
      .provides(List.of(new InterfaceDescriptor().id("bar-api").version("1.0").handlers(List.of(
        new RoutingEntry().methods(List.of("GET")).pathPattern("/bar-api/items")))));
  }

  private static Map<String, Object> fooFlowParams() {
    return Map.of(
      PARAM_MODULE_TYPE, MODULE,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID,
      PARAM_MODULE_ID, MOD_FOO_ID,
      PARAM_MODULE_DESCRIPTOR, fooModuleDesc(),
      PARAM_MODULE_DISCOVERY, "https://mod-foo:8443");
  }

  private static Map<String, Object> uiFooFlowParams() {
    return Map.of(
      PARAM_MODULE_TYPE, ModuleType.UI_MODULE,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID,
      PARAM_MODULE_ID, UI_FOO_ID,
      PARAM_MODULE_DESCRIPTOR, uiFooModuleDesc());
  }

  private static Map<String, Object> barFlowParams() {
    return Map.of(
      PARAM_MODULE_TYPE, MODULE,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID,
      PARAM_MODULE_ID, MOD_BAR_ID,
      PARAM_MODULE_DESCRIPTOR, barModuleDesc(),
      PARAM_MODULE_DISCOVERY, "https://mod-bar:8443");
  }

  private static Map<String, Object> deprecatedUiFooParams() {
    return Map.of(
      PARAM_MODULE_TYPE, UI_MODULE,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID,
      PARAM_MODULE_ID, UI_FOO_ID,
      PARAM_INSTALLED_MODULE_DESCRIPTOR, uiFooModuleDesc());
  }

  private static Map<String, Object> upgradeModFooV2Params() {
    return Map.of(
      PARAM_MODULE_TYPE, MODULE,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID,
      PARAM_MODULE_ID, MOD_FOO_V2_ID,
      PARAM_MODULE_DESCRIPTOR, fooModuleDescV2(),
      PARAM_INSTALLED_MODULE_DESCRIPTOR, fooModuleDesc(),
      PARAM_MODULE_DISCOVERY, "https://mod-foo-v2:8443");
  }

  private static Map<String, Object> upgradeModBarParams() {
    return Map.of(
      PARAM_MODULE_TYPE, MODULE,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID,
      PARAM_MODULE_ID, MOD_BAR_ID,
      PARAM_MODULE_DESCRIPTOR, barModuleDesc(),
      PARAM_INSTALLED_MODULE_DESCRIPTOR, barModuleDesc(),
      PARAM_MODULE_DISCOVERY, "https://mod-bar:8443");
  }

  private static @NotNull Map<String, Object> uiBarFlowParams() {
    return Map.of(
      PARAM_MODULE_TYPE, UI_MODULE,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID,
      PARAM_MODULE_ID, UI_BAR_ID,
      PARAM_MODULE_DESCRIPTOR, uiBarModuleDesc());
  }

  private static Map<String, String> moduleDiscoveryTable() {
    return Map.of(
      MOD_FOO_ID, "https://mod-foo:8443",
      MOD_BAR_ID, "https://mod-bar:8443",
      MOD_FOO_V2_ID, "https://mod-foo-v2:8443");
  }

  private static String getEntitleStageFlowId(EntitlementType type, int level, String moduleId) {
    return FLOW_STAGE_ID + "/FolioModules" + capitalize(type.getValue()) + "Flow/Level-" + level + "/" + moduleId;
  }

  private static String getDeprecatedStageFlowId(int level, String moduleId) {
    return FLOW_STAGE_ID + "/FolioModulesUpgradeFlow/Deprecated/Level-" + level + "/" + moduleId;
  }

  private static Module toModule(ModuleDescriptor moduleDescriptor) {
    var pattern = Pattern.compile("(.+)-(\\d\\.\\d\\.\\d.*)");
    var matcher = pattern.matcher(moduleDescriptor.getId());
    assertThat(matcher.matches()).isTrue();
    return module(matcher.group(1), matcher.group(2));
  }
}
