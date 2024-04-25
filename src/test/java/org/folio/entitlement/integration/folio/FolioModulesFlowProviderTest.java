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
  @Mock private FolioModuleRevokeFlowFactory folioModuleRevokeFlowFactory;
  @Mock private FolioModuleEntitleFlowFactory folioModuleEntitleFlowFactory;
  @Mock private FolioModuleUpgradeFlowFactory folioModuleUpgradeFlowFactory;
  @Mock private Stage<StageContext> moduleStage;

  private final FlowEngine flowEngine = singleThreadFlowEngine("folio-module-installer-flow-engine", false);

  @BeforeEach
  void setUp() {
    when(folioModuleEntitleFlowFactory.getEntitlementType()).thenReturn(ENTITLE);
    when(folioModuleRevokeFlowFactory.getEntitlementType()).thenReturn(REVOKE);
    when(folioModuleUpgradeFlowFactory.getEntitlementType()).thenReturn(UPGRADE);
    flowProvider = new FolioModulesFlowProvider(List.of(
      folioModuleEntitleFlowFactory, folioModuleRevokeFlowFactory, folioModuleUpgradeFlowFactory));
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
    when(folioModuleEntitleFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);
    var inOrder = inOrder(folioModuleEntitleFlowFactory, moduleStage);

    var flow = flowProvider.createFlow(stageContext);
    flowEngine.execute(flow);

    var fooFlowId = getEntitleStageFlowId(ENTITLE, 0, MOD_FOO_ID);
    var fooFlowParams = fooFlowParams();
    inOrder.verify(folioModuleEntitleFlowFactory).createModuleFlow(fooFlowId, IGNORE_ON_ERROR, fooFlowParams);
    inOrder.verify(moduleStage).execute(StageContext.of(fooFlowId, fooFlowParams, emptyMap()));
  }

  @Test
  void createFlow_positive_revokeRequestSingleModule() {
    var request = EntitlementRequest.builder().type(REVOKE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(List.of(fooModuleDesc()), emptyList());

    var flowParameters = flowParameters(request, applicationDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, Map.of(MOD_FOO_ID, "https://mod-foo:8443"));
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);
    mockStageNames(moduleStage);
    var inOrder = inOrder(folioModuleRevokeFlowFactory, moduleStage);
    when(folioModuleRevokeFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);

    var flow = flowProvider.createFlow(stageContext);
    flowEngine.execute(flow);

    var fooFlowId = getEntitleStageFlowId(REVOKE, 0, MOD_FOO_ID);
    var fooFlowParams = fooFlowParams();
    inOrder.verify(folioModuleRevokeFlowFactory).createModuleFlow(fooFlowId, IGNORE_ON_ERROR, fooFlowParams);
    inOrder.verify(moduleStage).execute(StageContext.of(fooFlowId, fooFlowParams, emptyMap()));
  }

  @Test
  void createFlow_positive_upgradeRequestSingleModule() {
    mockStageNames(moduleStage);
    when(folioModuleUpgradeFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);
    var inOrder = inOrder(folioModuleUpgradeFlowFactory, moduleStage);

    var request = EntitlementRequest.builder().type(UPGRADE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(List.of(fooModuleDescV2()), emptyList());
    var entitledAppDescriptor = appDescriptor(List.of(fooModuleDesc()), emptyList());
    var flowParameters = flowParameters(request, applicationDescriptor, entitledAppDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, Map.of(MOD_FOO_V2_ID, "https://mod-foo-v2:8443"));
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var flow = flowProvider.createFlow(stageContext);
    flowEngine.execute(flow);

    var fooFlowId = getEntitleStageFlowId(UPGRADE, 0, MOD_FOO_V2_ID);
    var fooFlowParameters = upgradeModFooV2Params();

    inOrder.verify(folioModuleUpgradeFlowFactory).createModuleFlow(fooFlowId, IGNORE_ON_ERROR, fooFlowParameters);
    inOrder.verify(moduleStage).execute(StageContext.of(fooFlowId, fooFlowParameters, emptyMap()));
  }

  @Test
  void createFlow_positive_upgradeRequestNewModules() {
    mockStageNames(moduleStage);
    when(folioModuleUpgradeFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);
    when(folioModuleUpgradeFlowFactory.createUiModuleFlow(any(), any(), any())).then(this::createFlow);
    var inOrder = inOrder(folioModuleUpgradeFlowFactory, moduleStage);

    var request = EntitlementRequest.builder().type(UPGRADE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(List.of(fooModuleDesc()), List.of(uiFooModuleDesc()));
    var entitledAppDescriptor = appDescriptor(emptyList(), emptyList());
    var flowParameters = flowParameters(request, applicationDescriptor, entitledAppDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, moduleDiscoveryTable());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

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

    inOrder.verify(folioModuleUpgradeFlowFactory).createModuleFlow(fooFlowId, IGNORE_ON_ERROR, fooFlowParameters);
    inOrder.verify(folioModuleUpgradeFlowFactory).createUiModuleFlow(uiFlowId, IGNORE_ON_ERROR, uiFooFlowParameters);
    inOrder.verify(moduleStage).execute(StageContext.of(fooFlowId, fooFlowParameters, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(uiFlowId, uiFooFlowParameters, emptyMap()));
  }

  @Test
  void createFlow_positive_upgradeRequestDeprecatedModule() {
    mockStageNames(moduleStage);
    when(folioModuleUpgradeFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);
    when(folioModuleUpgradeFlowFactory.createUiModuleFlow(any(), any(), any())).then(this::createFlow);
    var inOrder = inOrder(folioModuleUpgradeFlowFactory, moduleStage);

    var request = EntitlementRequest.builder().type(UPGRADE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(emptyList(), emptyList());
    var entitledAppDescriptor = appDescriptor(List.of(fooModuleDesc()), List.of(uiFooModuleDesc()));
    var flowParameters = flowParameters(request, applicationDescriptor, entitledAppDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, moduleDiscoveryTable());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var flow = flowProvider.createFlow(stageContext);
    flowEngine.execute(flow);

    var fooFlowId = getDeprecatedStageFlowId(1, MOD_FOO_ID);
    var fooFlowParameters = Map.of(
      PARAM_MODULE_TYPE, MODULE,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID,
      PARAM_MODULE_ID, MOD_FOO_ID,
      PARAM_INSTALLED_MODULE_DESCRIPTOR, fooModuleDesc(),
      PARAM_MODULE_DISCOVERY, "https://mod-foo:8443");

    var uiFooFlowId = getDeprecatedStageFlowId(0, UI_FOO_ID);
    var uiFlowParameters = deprecatedUiFooParams();

    inOrder.verify(folioModuleUpgradeFlowFactory).createUiModuleFlow(uiFooFlowId, IGNORE_ON_ERROR, uiFlowParameters);
    inOrder.verify(folioModuleUpgradeFlowFactory).createModuleFlow(fooFlowId, IGNORE_ON_ERROR, fooFlowParameters);
    inOrder.verify(moduleStage).execute(StageContext.of(uiFooFlowId, uiFlowParameters, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(fooFlowId, fooFlowParameters, emptyMap()));
  }

  @Test
  void createFlow_positive_complexEntitleRequest() {
    mockStageNames(moduleStage);
    when(folioModuleEntitleFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);
    when(folioModuleEntitleFlowFactory.createUiModuleFlow(any(), any(), any())).then(this::createFlow);
    var inOrder = inOrder(folioModuleEntitleFlowFactory, moduleStage);

    var request = EntitlementRequest.builder().type(ENTITLE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(List.of(fooModuleDesc(), barModuleDesc()), List.of(uiFooModuleDesc()));
    var flowParameters = flowParameters(request, applicationDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, moduleDiscoveryTable());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var flow = flowProvider.createFlow(stageContext);
    flowEngine.execute(flow);

    assertThat(flow).isNotNull();

    var fooFlowId = getEntitleStageFlowId(ENTITLE, 0, MOD_FOO_ID);
    var fooFlowParams = fooFlowParams();
    inOrder.verify(folioModuleEntitleFlowFactory).createModuleFlow(fooFlowId, IGNORE_ON_ERROR, fooFlowParams);

    var uiFooFlowId = getEntitleStageFlowId(ENTITLE, 1, UI_FOO_ID);
    var uiFooFlowParams = uiFooFlowParams();
    inOrder.verify(folioModuleEntitleFlowFactory).createUiModuleFlow(uiFooFlowId, IGNORE_ON_ERROR, uiFooFlowParams);

    var barFlowId = getEntitleStageFlowId(ENTITLE, 1, MOD_BAR_ID);
    var barFlowParams = barFlowParams();
    inOrder.verify(folioModuleEntitleFlowFactory).createModuleFlow(barFlowId, IGNORE_ON_ERROR, barFlowParams);

    inOrder.verify(moduleStage).execute(StageContext.of(fooFlowId, fooFlowParams, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(uiFooFlowId, uiFooFlowParams, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(barFlowId, barFlowParams, emptyMap()));
  }

  @Test
  void createFlow_positive_complexRevokeRequest() {
    mockStageNames(moduleStage);
    when(folioModuleRevokeFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);
    when(folioModuleRevokeFlowFactory.createUiModuleFlow(any(), any(), any())).then(this::createFlow);
    var inOrder = inOrder(folioModuleRevokeFlowFactory, moduleStage);

    var request = EntitlementRequest.builder().type(REVOKE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(List.of(fooModuleDesc(), barModuleDesc()), List.of(uiFooModuleDesc()));
    var flowParameters = flowParameters(request, applicationDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, moduleDiscoveryTable());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var flow = flowProvider.createFlow(stageContext);
    flowEngine.execute(flow);

    assertThat(flow).isNotNull();

    var uiFooFlowId = getEntitleStageFlowId(REVOKE, 0, UI_FOO_ID);
    var uiFooFlowParams = uiFooFlowParams();
    inOrder.verify(folioModuleRevokeFlowFactory).createUiModuleFlow(uiFooFlowId, IGNORE_ON_ERROR, uiFooFlowParams);

    var barFlowId = getEntitleStageFlowId(REVOKE, 0, MOD_BAR_ID);
    var barFlowParams = barFlowParams();
    inOrder.verify(folioModuleRevokeFlowFactory).createModuleFlow(barFlowId, IGNORE_ON_ERROR, barFlowParams);

    var fooFlowId = getEntitleStageFlowId(REVOKE, 1, MOD_FOO_ID);
    var fooFlowParams = fooFlowParams();
    inOrder.verify(folioModuleRevokeFlowFactory).createModuleFlow(fooFlowId, IGNORE_ON_ERROR, fooFlowParams);

    inOrder.verify(moduleStage).execute(StageContext.of(uiFooFlowId, uiFooFlowParams, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(barFlowId, barFlowParams, emptyMap()));
    inOrder.verify(moduleStage).execute(StageContext.of(fooFlowId, fooFlowParams, emptyMap()));
  }

  @Test
  void createFlow_positive_complexUpgradeRequest() {
    mockStageNames(moduleStage);
    when(folioModuleUpgradeFlowFactory.createModuleFlow(any(), any(), any())).then(this::createFlow);
    when(folioModuleUpgradeFlowFactory.createUiModuleFlow(any(), any(), any())).then(this::createFlow);
    var inOrder = inOrder(folioModuleUpgradeFlowFactory, moduleStage);

    var request = EntitlementRequest.builder().type(UPGRADE).ignoreErrors(true).build();
    var applicationDescriptor = appDescriptor(List.of(fooModuleDescV2(), barModuleDesc()), List.of(uiBarModuleDesc()));
    var entitledAppDescriptor = appDescriptor(List.of(fooModuleDesc(), barModuleDesc()), List.of(uiFooModuleDesc()));
    var flowParameters = flowParameters(request, applicationDescriptor, entitledAppDescriptor);
    var stageParameters = Map.of(PARAM_MODULE_DISCOVERY_DATA, moduleDiscoveryTable());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var flow = flowProvider.createFlow(stageContext);
    flowEngine.execute(flow);

    assertThat(flow).isNotNull();

    var fooV2FlowId = getEntitleStageFlowId(UPGRADE, 0, MOD_FOO_V2_ID);
    var fooV2FlowParams = upgradeModFooV2Params();
    inOrder.verify(folioModuleUpgradeFlowFactory).createModuleFlow(fooV2FlowId, IGNORE_ON_ERROR, fooV2FlowParams);

    var uiBarFlowId = getEntitleStageFlowId(UPGRADE, 1, UI_BAR_ID);
    var uiBarFlowParams = uiBarFlowParams();
    inOrder.verify(folioModuleUpgradeFlowFactory).createUiModuleFlow(uiBarFlowId, IGNORE_ON_ERROR, uiBarFlowParams);

    var uiFooFlowId = getDeprecatedStageFlowId(0, UI_FOO_ID);
    var uiFooFlowParams = deprecatedUiFooParams();
    inOrder.verify(folioModuleUpgradeFlowFactory).createUiModuleFlow(uiFooFlowId, IGNORE_ON_ERROR, uiFooFlowParams);

    inOrder.verify(moduleStage).execute(StageContext.of(fooV2FlowId, fooV2FlowParams, emptyMap()));
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
      .requires(List.of(new InterfaceReference().id("foo-api").version("1.0")))
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
