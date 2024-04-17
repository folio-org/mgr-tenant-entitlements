package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyMap;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
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
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCleaner;
import org.folio.entitlement.integration.kong.KongRouteCleaner;
import org.folio.entitlement.integration.okapi.OkapiModuleInstallerFlowProvider;
import org.folio.entitlement.service.stage.ApplicationDependencyCleaner;
import org.folio.entitlement.service.stage.ApplicationDiscoveryLoader;
import org.folio.entitlement.service.stage.ApplicationFlowInitializer;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.entitlement.service.stage.FailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.RevokeApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.RevokeRequestDependencyValidator;
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
class RevokeApplicationFlowFactoryTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("test-flow-engine", false);

  @InjectMocks private RevokeApplicationFlowFactory flowFactory;

  @Mock private ApplicationDiscoveryLoader applicationDiscoveryLoader;
  @Mock private ApplicationDependencyCleaner applicationDependencyCleaner;
  @Mock private RevokeRequestDependencyValidator revokeRequestDependencyValidator;

  @Mock private ApplicationFlowInitializer flowInitializer;
  @Mock private RevokeApplicationFlowFinalizer finishedFlowFinalizer;
  @Mock private FailedApplicationFlowFinalizer failedFlowFinalizer;
  @Mock private SkippedApplicationFlowFinalizer skippedFlowFinalizer;

  @Mock private KongRouteCleaner kongRouteCleaner;
  @Mock private OkapiModuleInstallerFlowProvider okapiModuleInstallerFlowProvider;
  @Mock private KeycloakAuthResourceCleaner kcAuthResourceCleaner;
  @Mock private ModuleInstallationFlowProvider folioModuleInstallerFlowProvider;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void prepareFlow_positive_folioInstallation() {
    mockStageNames(flowInitializer, applicationDependencyCleaner,
      revokeRequestDependencyValidator, applicationDiscoveryLoader, finishedFlowFinalizer,
      failedFlowFinalizer, kongRouteCleaner, kcAuthResourceCleaner, skippedFlowFinalizer);

    flowFactory.setKongRouteCleaner(kongRouteCleaner);
    flowFactory.setKeycloakAuthResourceCleaner(kcAuthResourceCleaner);
    flowFactory.setFolioModuleInstallerFlowProvider(folioModuleInstallerFlowProvider);

    var request = EntitlementRequest.builder().type(REVOKE).tenantId(TENANT_ID).build();
    var flowParameters = TestValues.flowParameters(request, applicationDescriptor());
    var actual = flowFactory.createFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(actual);

    var context = appStageContext(actual.getId(), flowParameters, emptyMap());

    var inOrder = Mockito.inOrder(flowInitializer, applicationDependencyCleaner,
      revokeRequestDependencyValidator, applicationDiscoveryLoader, finishedFlowFinalizer,
      kongRouteCleaner, kcAuthResourceCleaner, folioModuleInstallerFlowProvider);

    verifyStageExecution(inOrder, flowInitializer, context);
    verifyStageExecution(inOrder, revokeRequestDependencyValidator, context);
    verifyStageExecution(inOrder, applicationDiscoveryLoader, context);

    var moduleStageContext = StageContext.of(FLOW_STAGE_ID + "/ModuleUninstaller", flowParameters, emptyMap());
    var moduleInstallerContextWrapper = ApplicationStageContext.decorate(moduleStageContext);
    inOrder.verify(folioModuleInstallerFlowProvider).prepareFlow(moduleStageContext);

    verifyStageExecution(inOrder, kongRouteCleaner, moduleInstallerContextWrapper);
    verifyStageExecution(inOrder, kcAuthResourceCleaner, moduleInstallerContextWrapper);

    verifyStageExecution(inOrder, applicationDependencyCleaner, context);
    verifyStageExecution(inOrder, finishedFlowFinalizer, context);

    verify(skippedFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
    verify(failedFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
  }

  @Test
  void prepareFlow_positive_okapiInstallation() {
    mockStageNames(flowInitializer, applicationDependencyCleaner, revokeRequestDependencyValidator,
      applicationDiscoveryLoader, finishedFlowFinalizer, failedFlowFinalizer,
      skippedFlowFinalizer);

    flowFactory.setOkapiModuleInstallerFlowProvider(okapiModuleInstallerFlowProvider);
    var request = EntitlementRequest.builder().type(REVOKE).tenantId(TENANT_ID).build();
    var flowParameters = TestValues.flowParameters(request, applicationDescriptor());
    var actual = flowFactory.createFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(actual);

    var context = appStageContext(actual.getId(), flowParameters, emptyMap());

    var inOrder = Mockito.inOrder(flowInitializer, applicationDependencyCleaner,
      revokeRequestDependencyValidator, applicationDiscoveryLoader, finishedFlowFinalizer,
      okapiModuleInstallerFlowProvider, skippedFlowFinalizer);

    verifyStageExecution(inOrder, flowInitializer, context);
    verifyStageExecution(inOrder, revokeRequestDependencyValidator, context);
    verifyStageExecution(inOrder, applicationDiscoveryLoader, context);

    var moduleInstallerContext = StageContext.of(FLOW_STAGE_ID + "/ModuleUninstaller", flowParameters, emptyMap());
    inOrder.verify(okapiModuleInstallerFlowProvider).prepareFlow(moduleInstallerContext);

    verifyStageExecution(inOrder, applicationDependencyCleaner, context);
    verifyStageExecution(inOrder, finishedFlowFinalizer, context);

    verify(failedFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
  }

  @Test
  void prepareFlow_positive_noConditionalStages() {
    mockStageNames(flowInitializer, applicationDependencyCleaner, revokeRequestDependencyValidator,
      applicationDiscoveryLoader, finishedFlowFinalizer, failedFlowFinalizer,
      skippedFlowFinalizer);

    var request = EntitlementRequest.builder().type(REVOKE).tenantId(TENANT_ID).build();
    var flowParameters = TestValues.flowParameters(request, applicationDescriptor());
    var actual = flowFactory.createFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(actual);

    var context = appStageContext(actual.getId(), flowParameters, emptyMap());

    var inOrder = Mockito.inOrder(flowInitializer, applicationDependencyCleaner,
      revokeRequestDependencyValidator, applicationDiscoveryLoader, finishedFlowFinalizer,
      failedFlowFinalizer, skippedFlowFinalizer);

    verifyStageExecution(inOrder, flowInitializer, context);
    verifyStageExecution(inOrder, revokeRequestDependencyValidator, context);
    verifyStageExecution(inOrder, applicationDiscoveryLoader, context);
    verifyStageExecution(inOrder, applicationDependencyCleaner, context);
    verifyStageExecution(inOrder, finishedFlowFinalizer, context);

    verify(skippedFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
    verify(failedFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
  }

  private static <T extends IdentifiableStageContext> void verifyStageExecution(InOrder inOrder,
    DatabaseLoggingStage<T> stage, T context) {
    inOrder.verify(stage).onStart(context);
    inOrder.verify(stage).execute(context);
    inOrder.verify(stage).onSuccess(context);
  }
}
