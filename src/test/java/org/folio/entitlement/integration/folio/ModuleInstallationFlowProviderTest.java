package org.folio.entitlement.integration.folio;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_DISCOVERY;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_DISCOVERY_DATA;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_VERSION;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestUtils.stageResults;
import static org.folio.entitlement.support.TestValues.module;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.folio.flow.model.ExecutionStatus.FAILED;
import static org.folio.flow.model.ExecutionStatus.SKIPPED;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.am.model.Module;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.DynamicStage;
import org.folio.flow.api.Flow;
import org.folio.flow.api.FlowEngine;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;
import org.folio.flow.exception.FlowExecutionException;
import org.folio.flow.model.ExecutionStatus;
import org.folio.flow.model.StageResult;
import org.folio.security.domain.model.descriptor.InterfaceDescriptor;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ModuleInstallationFlowProviderTest {

  private static final String MODULE_LOCATION = "http://test:8081";
  private static final String MOD_FOO_ID = "mod-foo-1.0.0";
  private static final String MOD_BAR_ID = "mod-bar-1.0.0";
  private static final String MOD_BAZ_ID = "mod-baz-1.0.0";
  private static final String DYNAMIC_FLOW_ID_PARENT = FLOW_STAGE_ID + "/folio-module-installer";
  private static final String DYNAMIC_FLOW_ID = DYNAMIC_FLOW_ID_PARENT + "/level-0";
  private final FlowEngine flowEngine = singleThreadFlowEngine("folio-module-installer-flow-engine", false);

  @InjectMocks private ModuleInstallationFlowProvider flowProvider;
  @Mock private FolioModuleInstaller folioModuleInstaller;
  @Mock private FolioModuleUninstaller folioModuleUninstaller;
  @Mock private FolioModuleEventPublisher kafkaPublisherStage;

  @ParameterizedTest
  @EnumSource(value = EntitlementType.class)
  void execute_positive_singleModule(EntitlementType type) {
    var request = EntitlementRequest.builder().type(type).build();
    var contextParams = Map.of(
      PARAM_APP_DESCRIPTOR, applicationDescriptor(fooModule()),
      PARAM_MODULE_DISCOVERY_DATA, Map.of(MOD_FOO_ID, MODULE_LOCATION),
      PARAM_TENANT_NAME, TENANT_NAME);

    var moduleInstallerStage = getModuleInstallerStage(type);
    mockStageNames(moduleInstallerStage, kafkaPublisherStage);

    var flowToExecute = flowToExecute(contextParams, flowProvider, request);
    flowEngine.execute(flowToExecute);

    var expectedFlowParams = flowParameters(request, fooModule());
    var dynamicStageContext = StageContext.of(DYNAMIC_FLOW_ID + "/" + MOD_FOO_ID, expectedFlowParams, contextParams);
    verify(moduleInstallerStage).execute(dynamicStageContext);
    verify(kafkaPublisherStage).execute(dynamicStageContext);
  }

  @ParameterizedTest
  @EnumSource(value = EntitlementType.class)
  void execute_positive_singleModuleWithoutTenantApi(EntitlementType type) {
    var request = EntitlementRequest.builder().type(type).build();
    var contextParams = Map.of(
      PARAM_APP_DESCRIPTOR, applicationDescriptor(moduleWithoutTenantApi()),
      PARAM_MODULE_DISCOVERY_DATA, Map.of(MOD_BAZ_ID, MODULE_LOCATION),
      PARAM_TENANT_NAME, TENANT_NAME);

    var moduleInstallerStage = getModuleInstallerStage(type);
    mockStageNames(moduleInstallerStage, kafkaPublisherStage);

    var flowToExecute = flowToExecute(contextParams, flowProvider, request);
    flowEngine.execute(flowToExecute);

    var expectedFlowParams = flowParameters(request, moduleWithoutTenantApi());
    var dynamicStageContext = StageContext.of(DYNAMIC_FLOW_ID + "/" + MOD_BAZ_ID, expectedFlowParams, contextParams);
    verify(moduleInstallerStage).execute(dynamicStageContext);
    verify(kafkaPublisherStage).execute(dynamicStageContext);
  }

  @ParameterizedTest
  @EnumSource(value = EntitlementType.class)
  void execute_positive_parameterized(EntitlementType type) {
    var request = EntitlementRequest.builder().type(type).build();
    var contextParams = Map.of(
      PARAM_APP_DESCRIPTOR, applicationDescriptor(fooModule(), barModule()),
      PARAM_MODULE_DISCOVERY_DATA, Map.of(MOD_FOO_ID, MODULE_LOCATION, MOD_BAR_ID, MODULE_LOCATION),
      PARAM_TENANT_NAME, TENANT_NAME);

    var moduleInstallerStage = getModuleInstallerStage(type);
    mockStageNames(moduleInstallerStage, kafkaPublisherStage);

    var flowToExecute = flowToExecute(contextParams, flowProvider, request);
    flowEngine.execute(flowToExecute);

    var expectedFlowParams1 = flowParameters(request, fooModule());
    var dynamicStageCtx1 = StageContext.of(DYNAMIC_FLOW_ID + "/" + MOD_FOO_ID, expectedFlowParams1, contextParams);
    verify(moduleInstallerStage).execute(dynamicStageCtx1);
    verify(kafkaPublisherStage).execute(dynamicStageCtx1);

    var expectedFlowParams2 = flowParameters(request, barModule());
    var dynamicStageContext2 = StageContext.of(DYNAMIC_FLOW_ID + "/" + MOD_BAR_ID, expectedFlowParams2, contextParams);
    verify(moduleInstallerStage).execute(dynamicStageContext2);
    verify(kafkaPublisherStage).execute(dynamicStageContext2);
  }

  @ParameterizedTest
  @EnumSource(value = EntitlementType.class)
  void execute_negative_parameterized(EntitlementType type) {
    var request = EntitlementRequest.builder().type(type).ignoreErrors(true).build();
    var applicationDescriptor = applicationDescriptor(fooModule(), barModule());
    var contextParams = Map.of(
      PARAM_REQUEST, request,
      PARAM_APP_DESCRIPTOR, applicationDescriptor,
      PARAM_MODULE_DISCOVERY_DATA, Map.of(MOD_FOO_ID, MODULE_LOCATION, MOD_BAR_ID, MODULE_LOCATION),
      PARAM_TENANT_NAME, TENANT_NAME);

    var moduleInstallerStage = getModuleInstallerStage(type);
    mockStageNames(moduleInstallerStage, kafkaPublisherStage);

    var exception = new IntegrationException("Failed to execute request", emptyList());
    doThrow(IntegrationException.class).when(moduleInstallerStage).execute(any(StageContext.class));

    var parallelStageId = DYNAMIC_FLOW_ID;
    var flowToExecute = flowToExecute(contextParams, flowProvider, request);
    assertThatThrownBy(() -> flowEngine.execute(flowToExecute))
      .isInstanceOf(FlowExecutionException.class)
      .hasMessage("Failed to execute flow %s, stage '%s' failed", flowToExecute, parallelStageId)
      .extracting(error -> extractTestStageResults(stageResults(error)), list(StageResult.class))
      .containsExactly(
        stageResult(DYNAMIC_FLOW_ID_PARENT, parallelStageId, FAILED, exception, List.of(
          stageResult(DYNAMIC_FLOW_ID_PARENT, DYNAMIC_FLOW_ID + "/" + MOD_FOO_ID, FAILED, exception, List.of(
            stageResult(DYNAMIC_FLOW_ID + "/" + MOD_FOO_ID, moduleInstallerStage.toString(), FAILED),
            stageResult(DYNAMIC_FLOW_ID + "/" + MOD_FOO_ID, kafkaPublisherStage.toString(), SKIPPED)
          )),
          stageResult(DYNAMIC_FLOW_ID_PARENT, DYNAMIC_FLOW_ID + "/" + MOD_BAR_ID, FAILED, exception, List.of(
            stageResult(DYNAMIC_FLOW_ID + "/" + MOD_BAR_ID, moduleInstallerStage.toString(), FAILED),
            stageResult(DYNAMIC_FLOW_ID + "/" + MOD_BAR_ID, kafkaPublisherStage.toString(), SKIPPED)
          ))
        )));

    var expectedFlowParams1 = flowParameters(request, fooModule());
    var dynamicStageContext1 = StageContext.of(DYNAMIC_FLOW_ID + "/" + MOD_FOO_ID, expectedFlowParams1, contextParams);
    verify(moduleInstallerStage).execute(dynamicStageContext1);

    var expectedFlowParams2 = flowParameters(request, barModule());
    var dynamicStageContext2 = StageContext.of(DYNAMIC_FLOW_ID + "/" + MOD_BAR_ID, expectedFlowParams2, contextParams);
    verify(moduleInstallerStage).execute(dynamicStageContext2);
  }

  private DatabaseLoggingStage getModuleInstallerStage(EntitlementType type) {
    return type == EntitlementType.ENTITLE ? folioModuleInstaller : folioModuleUninstaller;
  }

  public static List<StageResult> extractTestStageResults(List<StageResult> stageResults) {
    return stageResults.get(1).getSubStageResults().get(0).getSubStageResults();
  }

  private static ApplicationDescriptor applicationDescriptor(ModuleDescriptor... moduleDescriptors) {
    var moduleDescriptorsList = Arrays.asList(moduleDescriptors);
    return new ApplicationDescriptor()
      .id(APPLICATION_ID)
      .name(APPLICATION_NAME)
      .version(APPLICATION_VERSION)
      .modules(mapItems(moduleDescriptorsList, ModuleInstallationFlowProviderTest::toModule))
      .moduleDescriptors(moduleDescriptorsList);
  }

  private static ModuleDescriptor fooModule() {
    return new ModuleDescriptor().id(MOD_FOO_ID).provides(List.of(
      new InterfaceDescriptor().id("mod-foo-int"),
      new InterfaceDescriptor().id("_tenant").interfaceType("system")
    ));
  }

  private static ModuleDescriptor barModule() {
    return new ModuleDescriptor().id(MOD_BAR_ID).provides(List.of(
      new InterfaceDescriptor().id("mod-bar-int"),
      new InterfaceDescriptor().id("_tenant").interfaceType("system")
    ));
  }

  private static ModuleDescriptor moduleWithoutTenantApi() {
    return new ModuleDescriptor().id(MOD_BAZ_ID).provides(List.of(
      new InterfaceDescriptor().id("mod-baz-int")
    ));
  }

  private static StageResult stageResult(String flowId, String stageName,
    ExecutionStatus status, Exception error, List<StageResult> results) {
    return StageResult.builder()
      .flowId(flowId)
      .stageName(stageName)
      .status(status)
      .error(error)
      .subStageResults(results)
      .build();
  }

  private static StageResult stageResult(String flowId, String stageName, ExecutionStatus status) {
    return stageResult(flowId, stageName, status, null, emptyList());
  }

  private static Map<String, Object> flowParameters(EntitlementRequest request, ModuleDescriptor descriptor) {
    return Map.of(
      PARAM_MODULE_DISCOVERY, MODULE_LOCATION,
      PARAM_MODULE_ID, descriptor.getId(),
      PARAM_MODULE_DESCRIPTOR, descriptor,
      PARAM_REQUEST, request);
  }

  private static Module toModule(ModuleDescriptor moduleDescriptor) {
    var pattern = Pattern.compile("(.+)-(\\d\\.\\d\\.\\d.*)");
    var matcher = pattern.matcher(moduleDescriptor.getId());
    assertThat(matcher.matches()).isTrue();
    return module(matcher.group(1), matcher.group(2));
  }

  private static Flow flowToExecute(Map<String, Object> contextParameters,
    ModuleInstallationFlowProvider provider, EntitlementRequest request) {
    var spyStage = spy(Stage.class);
    doAnswer(invocation -> {
      var ctx = invocation.<StageContext>getArgument(0);
      contextParameters.forEach(ctx::put);
      return null;
    }).when(spyStage).execute(any(StageContext.class));
    when(spyStage.getId()).thenReturn("testContextSetter");
    return Flow.builder()
      .id(FLOW_STAGE_ID)
      .flowParameter(PARAM_REQUEST, request)
      .executionStrategy(IGNORE_ON_ERROR)
      .stage(spyStage)
      .stage(DynamicStage.of(DYNAMIC_FLOW_ID, provider::prepareFlow))
      .build();
  }
}
