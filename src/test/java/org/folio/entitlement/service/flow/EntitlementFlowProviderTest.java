package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyMap;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.folio.entitlement.integration.folio.ModuleInstallationFlowProvider;
import org.folio.entitlement.integration.kafka.CapabilitiesEventPublisher;
import org.folio.entitlement.integration.kafka.KafkaTenantTopicCreator;
import org.folio.entitlement.integration.kafka.ScheduledJobEventPublisher;
import org.folio.entitlement.integration.kafka.SystemUserEventPublisher;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCreator;
import org.folio.entitlement.integration.kong.KongRouteCreator;
import org.folio.entitlement.integration.okapi.OkapiModuleInstallerFlowProvider;
import org.folio.entitlement.service.stage.ApplicationDependencySaver;
import org.folio.entitlement.service.stage.ApplicationDescriptorLoader;
import org.folio.entitlement.service.stage.ApplicationDescriptorValidator;
import org.folio.entitlement.service.stage.ApplicationDiscoveryLoader;
import org.folio.entitlement.service.stage.CancellationFailedFlowFinalizer;
import org.folio.entitlement.service.stage.CancelledFlowFinalizer;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.entitlement.service.stage.EntitlementDependencyValidator;
import org.folio.entitlement.service.stage.EntitlementFlowFinalizer;
import org.folio.entitlement.service.stage.EntitlementFlowInitializer;
import org.folio.entitlement.service.stage.FailedFlowFinalizer;
import org.folio.entitlement.service.stage.TenantLoader;
import org.folio.entitlement.support.TestUtils;
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
class EntitlementFlowProviderTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("efp-test-flow-engine", false);

  @InjectMocks private EntitlementFlowProvider entitlementFlowProvider;

  @Mock private TenantLoader tenantLoader;
  @Mock private ApplicationDescriptorLoader applicationDescriptorLoader;
  @Mock private ApplicationDescriptorValidator applicationDescriptorValidator;
  @Mock private ApplicationDependencySaver applicationDependencySaver;
  @Mock private ApplicationDiscoveryLoader applicationDiscoveryLoader;
  @Mock private EntitlementDependencyValidator entitlementDependencyValidator;
  @Mock private KafkaTenantTopicCreator kafkaTenantTopicCreator;
  @Mock private CapabilitiesEventPublisher capabilitiesEventPublisher;
  @Mock private EntitlementFlowFinalizer entitlementFlowFinalizer;
  @Mock private ScheduledJobEventPublisher scheduledJobEventPublisher;
  @Mock private SystemUserEventPublisher systemUserEventPublisher;

  @Mock private EntitlementFlowInitializer flowInitializer;
  @Mock private FailedFlowFinalizer failedFlowFinalizer;
  @Mock private CancelledFlowFinalizer cancelledFlowFinalizer;
  @Mock private CancellationFailedFlowFinalizer cancellationFailedFlowFinalizer;

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
    entitlementFlowProvider.setKongRouteCreator(kongRouteCreator);
    entitlementFlowProvider.setKeycloakAuthResourceCreator(kcAuthResourceCreator);
    entitlementFlowProvider.setFolioModuleInstallerFlowProvider(folioModuleInstallerFlowProvider);

    mockStageNames(tenantLoader, applicationDescriptorValidator, applicationDependencySaver,
      entitlementDependencyValidator, applicationDiscoveryLoader, kafkaTenantTopicCreator, applicationDescriptorLoader,
      capabilitiesEventPublisher, scheduledJobEventPublisher, systemUserEventPublisher, entitlementFlowFinalizer,
      flowInitializer, failedFlowFinalizer, kongRouteCreator, kcAuthResourceCreator,
      cancelledFlowFinalizer, cancellationFailedFlowFinalizer);

    var actual = entitlementFlowProvider.prepareFlow(FLOW_STAGE_ID, APPLICATION_ID, IGNORE_ON_ERROR);
    flowEngine.execute(actual);

    var flowParameters = Map.of(PARAM_APP_ID, APPLICATION_ID);
    var context = StageContext.of(actual.getId(), flowParameters, emptyMap());

    var inOrder = Mockito.inOrder(flowInitializer, tenantLoader, applicationDescriptorLoader,
      applicationDependencySaver, entitlementDependencyValidator, applicationDiscoveryLoader,
      entitlementFlowFinalizer, applicationDescriptorValidator, kafkaTenantTopicCreator,
      scheduledJobEventPublisher, capabilitiesEventPublisher, systemUserEventPublisher, kongRouteCreator,
      kcAuthResourceCreator, folioModuleInstallerFlowProvider);

    verifyStageExecution(inOrder, flowInitializer, context);
    verifyStageExecution(inOrder, tenantLoader, context);
    verifyStageExecution(inOrder, applicationDescriptorLoader, context);
    verifyStageExecution(inOrder, applicationDescriptorValidator, context);
    verifyStageExecution(inOrder, applicationDependencySaver, context);
    verifyStageExecution(inOrder, entitlementDependencyValidator, context);
    verifyStageExecution(inOrder, applicationDiscoveryLoader, context);
    verifyStageExecution(inOrder, kafkaTenantTopicCreator, context);

    var moduleInstallerContext = StageContext.of(FLOW_STAGE_ID + "/module-installer", flowParameters, emptyMap());
    verifyStageExecution(inOrder, kongRouteCreator, moduleInstallerContext);
    verifyStageExecution(inOrder, kcAuthResourceCreator, moduleInstallerContext);
    inOrder.verify(folioModuleInstallerFlowProvider).prepareFlow(moduleInstallerContext);
    verifyStageExecution(inOrder, scheduledJobEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, capabilitiesEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, systemUserEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, entitlementFlowFinalizer, context);

    verify(failedFlowFinalizer, never()).execute(any(StageContext.class));
    verify(cancelledFlowFinalizer, never()).execute(any(StageContext.class));
  }

  @Test
  void prepareFlow_positive_okapiInstallation() {
    entitlementFlowProvider.setOkapiModuleInstallerFlowProvider(okapiModuleInstallerFlowProvider);

    mockStageNames(tenantLoader, applicationDescriptorValidator, applicationDependencySaver,
      entitlementDependencyValidator, applicationDiscoveryLoader, kafkaTenantTopicCreator, applicationDescriptorLoader,
      capabilitiesEventPublisher, scheduledJobEventPublisher, systemUserEventPublisher, entitlementFlowFinalizer,
      flowInitializer, failedFlowFinalizer, cancelledFlowFinalizer, cancellationFailedFlowFinalizer);

    var actual = entitlementFlowProvider.prepareFlow(FLOW_STAGE_ID, APPLICATION_ID, IGNORE_ON_ERROR);
    flowEngine.execute(actual);

    var flowParameters = Map.of(PARAM_APP_ID, APPLICATION_ID);
    var context = StageContext.of(FLOW_STAGE_ID, flowParameters, emptyMap());

    var inOrder = Mockito.inOrder(flowInitializer, tenantLoader, applicationDescriptorLoader,
      applicationDependencySaver, entitlementDependencyValidator, applicationDiscoveryLoader,
      entitlementFlowFinalizer, applicationDescriptorValidator, kafkaTenantTopicCreator,
      scheduledJobEventPublisher, capabilitiesEventPublisher, systemUserEventPublisher,
      okapiModuleInstallerFlowProvider);

    verifyStageExecution(inOrder, flowInitializer, context);
    verifyStageExecution(inOrder, tenantLoader, context);
    verifyStageExecution(inOrder, applicationDescriptorLoader, context);
    verifyStageExecution(inOrder, applicationDescriptorValidator, context);
    verifyStageExecution(inOrder, applicationDependencySaver, context);
    verifyStageExecution(inOrder, entitlementDependencyValidator, context);
    verifyStageExecution(inOrder, applicationDiscoveryLoader, context);
    verifyStageExecution(inOrder, kafkaTenantTopicCreator, context);

    var moduleInstallerContext = StageContext.of(FLOW_STAGE_ID + "/module-installer", flowParameters, emptyMap());
    inOrder.verify(okapiModuleInstallerFlowProvider).prepareFlow(moduleInstallerContext);
    verifyStageExecution(inOrder, scheduledJobEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, capabilitiesEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, systemUserEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, entitlementFlowFinalizer, context);

    verify(failedFlowFinalizer, never()).execute(any(StageContext.class));
    verify(cancelledFlowFinalizer, never()).execute(any(StageContext.class));
  }

  @Test
  void prepareFlow_positive_noConditionalStages() {
    mockStageNames(tenantLoader, applicationDescriptorValidator, applicationDependencySaver,
      entitlementDependencyValidator, applicationDiscoveryLoader, kafkaTenantTopicCreator, applicationDescriptorLoader,
      capabilitiesEventPublisher, scheduledJobEventPublisher, systemUserEventPublisher, entitlementFlowFinalizer,
      flowInitializer, failedFlowFinalizer, cancelledFlowFinalizer, cancellationFailedFlowFinalizer);

    var actual = entitlementFlowProvider.prepareFlow(FLOW_STAGE_ID, APPLICATION_ID, IGNORE_ON_ERROR);
    flowEngine.execute(actual);

    var inOrder = Mockito.inOrder(flowInitializer, tenantLoader, applicationDescriptorLoader,
      applicationDependencySaver, entitlementDependencyValidator, applicationDiscoveryLoader,
      entitlementFlowFinalizer, applicationDescriptorValidator, kafkaTenantTopicCreator,
      scheduledJobEventPublisher, capabilitiesEventPublisher, systemUserEventPublisher);

    var flowParameters = Map.of(PARAM_APP_ID, APPLICATION_ID);
    var context = StageContext.of(FLOW_STAGE_ID, flowParameters, emptyMap());

    verifyStageExecution(inOrder, flowInitializer, context);
    verifyStageExecution(inOrder, tenantLoader, context);
    verifyStageExecution(inOrder, applicationDescriptorLoader, context);
    verifyStageExecution(inOrder, applicationDescriptorValidator, context);
    verifyStageExecution(inOrder, applicationDependencySaver, context);
    verifyStageExecution(inOrder, entitlementDependencyValidator, context);
    verifyStageExecution(inOrder, applicationDiscoveryLoader, context);
    verifyStageExecution(inOrder, kafkaTenantTopicCreator, context);

    var moduleInstallerContext = StageContext.of(FLOW_STAGE_ID + "/module-installer", flowParameters, emptyMap());
    verifyStageExecution(inOrder, scheduledJobEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, capabilitiesEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, systemUserEventPublisher, moduleInstallerContext);
    verifyStageExecution(inOrder, entitlementFlowFinalizer, context);

    verify(failedFlowFinalizer, never()).execute(any(StageContext.class));
    verify(cancelledFlowFinalizer, never()).execute(any(StageContext.class));
  }

  private static void verifyStageExecution(InOrder inOrder, DatabaseLoggingStage stage, StageContext context) {
    inOrder.verify(stage).onStart(context);
    inOrder.verify(stage).execute(context);
    inOrder.verify(stage).onSuccess(context);
  }
}
