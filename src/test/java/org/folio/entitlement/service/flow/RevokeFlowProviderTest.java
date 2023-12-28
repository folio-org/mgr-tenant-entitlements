package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyMap;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.folio.entitlement.integration.folio.ModuleInstallationFlowProvider;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCleaner;
import org.folio.entitlement.integration.kong.KongRouteCleaner;
import org.folio.entitlement.integration.okapi.OkapiModuleInstallerFlowProvider;
import org.folio.entitlement.service.stage.ApplicationDependencyCleaner;
import org.folio.entitlement.service.stage.ApplicationDescriptorLoader;
import org.folio.entitlement.service.stage.ApplicationDiscoveryLoader;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.entitlement.service.stage.EntitlementDependencyValidator;
import org.folio.entitlement.service.stage.EntitlementFlowInitializer;
import org.folio.entitlement.service.stage.FailedFlowFinalizer;
import org.folio.entitlement.service.stage.RevokeFlowFinalizer;
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
class RevokeFlowProviderTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("rfp-test-flow-engine", false);

  @InjectMocks private RevokeFlowProvider revokeFlowProvider;

  @Mock private TenantLoader tenantLoader;
  @Mock private ApplicationDiscoveryLoader applicationDiscoveryLoader;
  @Mock private ApplicationDescriptorLoader applicationDescriptorLoader;
  @Mock private ApplicationDependencyCleaner applicationDependencyCleaner;
  @Mock private EntitlementDependencyValidator entitlementDependencyValidator;

  @Mock private RevokeFlowFinalizer revokeFlowFinalizer;
  @Mock private EntitlementFlowInitializer entitlementFlowInitializer;
  @Mock private FailedFlowFinalizer failedFlowFinalizer;

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
    mockStageNames(entitlementFlowInitializer, tenantLoader, applicationDescriptorLoader,
      applicationDependencyCleaner, entitlementDependencyValidator, applicationDiscoveryLoader,
      revokeFlowFinalizer, failedFlowFinalizer, kongRouteCleaner, kcAuthResourceCleaner);

    revokeFlowProvider.setKongRouteCleaner(kongRouteCleaner);
    revokeFlowProvider.setKeycloakAuthResourceCleaner(kcAuthResourceCleaner);
    revokeFlowProvider.setFolioModuleInstallerFlowProvider(folioModuleInstallerFlowProvider);

    var actual = revokeFlowProvider.prepareFlow(FLOW_STAGE_ID, APPLICATION_ID);
    flowEngine.execute(actual);

    var flowParameters = Map.of(PARAM_APP_ID, APPLICATION_ID);
    var context = StageContext.of(actual.getId(), flowParameters, emptyMap());

    var inOrder = Mockito.inOrder(entitlementFlowInitializer, tenantLoader, applicationDescriptorLoader,
      applicationDependencyCleaner, entitlementDependencyValidator, applicationDiscoveryLoader,
      revokeFlowFinalizer, kongRouteCleaner, kcAuthResourceCleaner, folioModuleInstallerFlowProvider);

    verifyStageExecution(inOrder, entitlementFlowInitializer, context);
    verifyStageExecution(inOrder, tenantLoader, context);
    verifyStageExecution(inOrder, applicationDescriptorLoader, context);
    verifyStageExecution(inOrder, entitlementDependencyValidator, context);
    verifyStageExecution(inOrder, applicationDiscoveryLoader, context);

    var moduleInstallerContext = StageContext.of(FLOW_STAGE_ID + "/module-uninstaller", flowParameters, emptyMap());
    inOrder.verify(folioModuleInstallerFlowProvider).prepareFlow(moduleInstallerContext);
    verifyStageExecution(inOrder, kongRouteCleaner, moduleInstallerContext);
    verifyStageExecution(inOrder, kcAuthResourceCleaner, moduleInstallerContext);

    verifyStageExecution(inOrder, applicationDependencyCleaner, context);
    verifyStageExecution(inOrder, revokeFlowFinalizer, context);

    verify(failedFlowFinalizer, never()).execute(any(StageContext.class));
  }

  @Test
  void prepareFlow_positive_okapiInstallation() {
    mockStageNames(entitlementFlowInitializer, tenantLoader, applicationDescriptorLoader,
      applicationDependencyCleaner, entitlementDependencyValidator, applicationDiscoveryLoader,
      revokeFlowFinalizer, failedFlowFinalizer);

    revokeFlowProvider.setOkapiModuleInstallerFlowProvider(okapiModuleInstallerFlowProvider);
    var actual = revokeFlowProvider.prepareFlow(FLOW_STAGE_ID, APPLICATION_ID);
    flowEngine.execute(actual);

    var flowParameters = Map.of(PARAM_APP_ID, APPLICATION_ID);
    var context = StageContext.of(actual.getId(), flowParameters, emptyMap());

    var inOrder = Mockito.inOrder(entitlementFlowInitializer, tenantLoader, applicationDescriptorLoader,
      applicationDependencyCleaner, entitlementDependencyValidator, applicationDiscoveryLoader,
      revokeFlowFinalizer, okapiModuleInstallerFlowProvider);

    verifyStageExecution(inOrder, entitlementFlowInitializer, context);
    verifyStageExecution(inOrder, tenantLoader, context);
    verifyStageExecution(inOrder, applicationDescriptorLoader, context);
    verifyStageExecution(inOrder, entitlementDependencyValidator, context);
    verifyStageExecution(inOrder, applicationDiscoveryLoader, context);

    var moduleInstallerContext = StageContext.of(FLOW_STAGE_ID + "/module-uninstaller", flowParameters, emptyMap());
    inOrder.verify(okapiModuleInstallerFlowProvider).prepareFlow(moduleInstallerContext);

    verifyStageExecution(inOrder, applicationDependencyCleaner, context);
    verifyStageExecution(inOrder, revokeFlowFinalizer, context);

    verify(failedFlowFinalizer, never()).execute(any(StageContext.class));
  }

  @Test
  void prepareFlow_positive_noConditionalStages() {
    mockStageNames(entitlementFlowInitializer, tenantLoader, applicationDescriptorLoader,
      applicationDependencyCleaner, entitlementDependencyValidator, applicationDiscoveryLoader,
      revokeFlowFinalizer, failedFlowFinalizer);

    var actual = revokeFlowProvider.prepareFlow(FLOW_STAGE_ID, APPLICATION_ID);
    flowEngine.execute(actual);

    var flowParameters = Map.of(PARAM_APP_ID, APPLICATION_ID);
    var context = StageContext.of(actual.getId(), flowParameters, emptyMap());

    var inOrder = Mockito.inOrder(entitlementFlowInitializer, tenantLoader, applicationDescriptorLoader,
      applicationDependencyCleaner, entitlementDependencyValidator, applicationDiscoveryLoader, revokeFlowFinalizer);

    verifyStageExecution(inOrder, entitlementFlowInitializer, context);
    verifyStageExecution(inOrder, tenantLoader, context);
    verifyStageExecution(inOrder, applicationDescriptorLoader, context);
    verifyStageExecution(inOrder, entitlementDependencyValidator, context);
    verifyStageExecution(inOrder, applicationDiscoveryLoader, context);
    verifyStageExecution(inOrder, applicationDependencyCleaner, context);
    verifyStageExecution(inOrder, revokeFlowFinalizer, context);

    verify(failedFlowFinalizer, never()).execute(any(StageContext.class));
  }

  private static void verifyStageExecution(InOrder inOrder, DatabaseLoggingStage stage, StageContext context) {
    inOrder.verify(stage).onStart(context);
    inOrder.verify(stage).execute(context);
    inOrder.verify(stage).onSuccess(context);
  }
}
