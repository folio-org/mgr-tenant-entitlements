package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyMap;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.folio.ApplicationStageContext;
import org.folio.entitlement.integration.folio.IdentifiableStageContext;
import org.folio.entitlement.integration.folio.ModuleInstallationFlowProvider;
import org.folio.entitlement.integration.kafka.CapabilitiesEventPublisher;
import org.folio.entitlement.integration.kafka.KafkaTenantTopicCreator;
import org.folio.entitlement.integration.kafka.ScheduledJobEventPublisher;
import org.folio.entitlement.integration.kafka.SystemUserEventPublisher;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCreator;
import org.folio.entitlement.integration.kong.KongRouteCreator;
import org.folio.entitlement.integration.okapi.OkapiModuleInstallerFlowProvider;
import org.folio.entitlement.service.stage.ApplicationDependencySaver;
import org.folio.entitlement.service.stage.ApplicationDescriptorValidator;
import org.folio.entitlement.service.stage.ApplicationDiscoveryLoader;
import org.folio.entitlement.service.stage.ApplicationFlowInitializer;
import org.folio.entitlement.service.stage.CancellationFailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.CancelledApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.entitlement.service.stage.EntitleApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.EntitleRequestDependencyValidator;
import org.folio.entitlement.service.stage.FailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.SkippedApplicationFlowFinalizer;
import org.folio.entitlement.support.TestUtils;
import org.folio.entitlement.support.TestValues;
import org.folio.flow.api.FlowEngine;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitleApplicationFlowFactoryTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("test-flow-engine", false);

  @InjectMocks private EntitleApplicationFlowFactory flowFactory;

  @Mock private ApplicationDescriptorValidator applicationDescriptorValidator;
  @Mock private ApplicationDependencySaver applicationDependencySaver;
  @Mock private ApplicationDiscoveryLoader applicationDiscoveryLoader;
  @Mock private EntitleRequestDependencyValidator entitleRequestDependencyValidator;
  @Mock private KafkaTenantTopicCreator kafkaTenantTopicCreator;
  @Mock private CapabilitiesEventPublisher capabilitiesEventPublisher;
  @Mock private ScheduledJobEventPublisher scheduledJobEventPublisher;
  @Mock private SystemUserEventPublisher systemUserEventPublisher;

  @Mock private ApplicationFlowInitializer flowInitializer;
  @Mock private FailedApplicationFlowFinalizer failedFlowFinalizer;
  @Mock private SkippedApplicationFlowFinalizer skippedFlowFinalizer;
  @Mock private EntitleApplicationFlowFinalizer finishedFlowFinalizer;
  @Mock private CancelledApplicationFlowFinalizer cancelledFlowFinalizer;
  @Mock private CancellationFailedApplicationFlowFinalizer cancellationFailedApplicationFlowFinalizer;

  @Mock private KongRouteCreator kongRouteCreator;
  @Mock private OkapiModuleInstallerFlowProvider okapiModuleInstallerFlowProvider;
  @Mock private KeycloakAuthResourceCreator kcAuthResourceCreator;
  @Mock private ModuleInstallationFlowProvider folioModuleInstallerFlowProvider;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void prepareFlow_positive_folioInstallation() {
    flowFactory.setKongRouteCreator(kongRouteCreator);
    flowFactory.setKeycloakAuthResourceCreator(kcAuthResourceCreator);
    flowFactory.setFolioModuleInstallerFlowProvider(folioModuleInstallerFlowProvider);

    mockStageNames(applicationDescriptorValidator, applicationDependencySaver,
      entitleRequestDependencyValidator, applicationDiscoveryLoader, kafkaTenantTopicCreator,
      capabilitiesEventPublisher, scheduledJobEventPublisher, systemUserEventPublisher,
      finishedFlowFinalizer, flowInitializer, failedFlowFinalizer, kongRouteCreator,
      kcAuthResourceCreator, cancelledFlowFinalizer, cancellationFailedApplicationFlowFinalizer, skippedFlowFinalizer);

    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = TestValues.flowParameters(request, applicationDescriptor());
    var actual = flowFactory.createFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(actual);

    var context = appStageContext(actual.getId(), flowParameters, emptyMap());

    var inOrder = Mockito.inOrder(flowInitializer, applicationDependencySaver, entitleRequestDependencyValidator,
      applicationDiscoveryLoader, finishedFlowFinalizer, applicationDescriptorValidator,
      kafkaTenantTopicCreator, scheduledJobEventPublisher, capabilitiesEventPublisher, systemUserEventPublisher,
      kongRouteCreator, kcAuthResourceCreator, folioModuleInstallerFlowProvider);

    verifyStageExecution(inOrder, flowInitializer, context);
    verifyStageExecution(inOrder, applicationDescriptorValidator, context);
    verifyStageExecution(inOrder, applicationDependencySaver, context);
    verifyStageExecution(inOrder, entitleRequestDependencyValidator, context);
    verifyStageExecution(inOrder, applicationDiscoveryLoader, context);
    verifyStageExecution(inOrder, kafkaTenantTopicCreator, context);

    var stageContext = StageContext.of(FLOW_STAGE_ID + "/ModuleInstaller", flowParameters, emptyMap());
    var moduleInstallerContext = ApplicationStageContext.decorate(stageContext);
    verifyStageExecution(inOrder, kongRouteCreator, moduleInstallerContext);
    verifyStageExecution(inOrder, kcAuthResourceCreator, moduleInstallerContext);
    inOrder.verify(folioModuleInstallerFlowProvider).prepareFlow(stageContext);
    verifyStageExecution(inOrder, scheduledJobEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, capabilitiesEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, systemUserEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, finishedFlowFinalizer, context);

    verify(failedFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
    verify(skippedFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
    verify(cancelledFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
  }

  @Test
  void prepareFlow_positive_okapiInstallation() {
    flowFactory.setOkapiModuleInstallerFlowProvider(okapiModuleInstallerFlowProvider);

    mockStageNames(applicationDescriptorValidator, applicationDependencySaver, entitleRequestDependencyValidator,
      applicationDiscoveryLoader, kafkaTenantTopicCreator, capabilitiesEventPublisher, scheduledJobEventPublisher,
      systemUserEventPublisher, finishedFlowFinalizer, flowInitializer, failedFlowFinalizer,
      cancelledFlowFinalizer, cancellationFailedApplicationFlowFinalizer, skippedFlowFinalizer);

    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = TestValues.flowParameters(request, applicationDescriptor());
    var actual = flowFactory.createFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(actual);

    var context = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var inOrder = Mockito.inOrder(flowInitializer, applicationDependencySaver, entitleRequestDependencyValidator,
      applicationDiscoveryLoader, finishedFlowFinalizer, applicationDescriptorValidator,
      kafkaTenantTopicCreator, scheduledJobEventPublisher, capabilitiesEventPublisher, systemUserEventPublisher,
      okapiModuleInstallerFlowProvider);

    verifyStageExecution(inOrder, flowInitializer, context);
    verifyStageExecution(inOrder, applicationDescriptorValidator, context);
    verifyStageExecution(inOrder, applicationDependencySaver, context);
    verifyStageExecution(inOrder, entitleRequestDependencyValidator, context);
    verifyStageExecution(inOrder, applicationDiscoveryLoader, context);
    verifyStageExecution(inOrder, kafkaTenantTopicCreator, context);

    var stageContext = StageContext.of(FLOW_STAGE_ID + "/ModuleInstaller", flowParameters, emptyMap());
    var stageContextWrapper = ApplicationStageContext.decorate(stageContext);
    inOrder.verify(okapiModuleInstallerFlowProvider).prepareFlow(stageContext);
    verifyStageExecution(inOrder, scheduledJobEventPublisher, stageContextWrapper);
    verifyStageExecution(inOrder, capabilitiesEventPublisher, stageContextWrapper);
    verifyStageExecution(inOrder, systemUserEventPublisher, stageContextWrapper);
    verifyStageExecution(inOrder, finishedFlowFinalizer, context);

    verify(failedFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
    verify(skippedFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
    verify(cancelledFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
  }

  @Test
  void prepareFlow_positive_noConditionalStages() {
    mockStageNames(applicationDescriptorValidator, applicationDependencySaver, entitleRequestDependencyValidator,
      applicationDiscoveryLoader, kafkaTenantTopicCreator, capabilitiesEventPublisher, scheduledJobEventPublisher,
      systemUserEventPublisher, finishedFlowFinalizer, flowInitializer, failedFlowFinalizer,
      cancelledFlowFinalizer, cancellationFailedApplicationFlowFinalizer, skippedFlowFinalizer);

    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = TestValues.flowParameters(request, applicationDescriptor());
    var actual = flowFactory.createFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(actual);

    var inOrder = Mockito.inOrder(flowInitializer, applicationDependencySaver, entitleRequestDependencyValidator,
      applicationDiscoveryLoader, finishedFlowFinalizer, applicationDescriptorValidator,
      kafkaTenantTopicCreator, scheduledJobEventPublisher, capabilitiesEventPublisher, systemUserEventPublisher);

    var context = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    verifyStageExecution(inOrder, flowInitializer, context);
    verifyStageExecution(inOrder, applicationDescriptorValidator, context);
    verifyStageExecution(inOrder, applicationDependencySaver, context);
    verifyStageExecution(inOrder, entitleRequestDependencyValidator, context);
    verifyStageExecution(inOrder, applicationDiscoveryLoader, context);
    verifyStageExecution(inOrder, kafkaTenantTopicCreator, context);

    var moduleInstallerContext = appStageContext(FLOW_STAGE_ID + "/ModuleInstaller", flowParameters, emptyMap());
    verifyStageExecution(inOrder, scheduledJobEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, capabilitiesEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, systemUserEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, finishedFlowFinalizer, context);

    verify(failedFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
    verify(skippedFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
    verify(cancelledFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
  }

  private static <T extends IdentifiableStageContext> void verifyStageExecution(InOrder inOrder,
    DatabaseLoggingStage<T> stage, T context) {
    inOrder.verify(stage).onStart(context);
    inOrder.verify(stage).execute(context);
    inOrder.verify(stage).onSuccess(context);
  }
}
