package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyMap;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;

import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.service.stage.ApplicationDependencyUpdater;
import org.folio.entitlement.service.stage.ApplicationDiscoveryLoader;
import org.folio.entitlement.service.stage.ApplicationFlowInitializer;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.entitlement.service.stage.FailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.SkippedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.UpgradeApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.UpgradeRequestDependencyValidator;
import org.folio.flow.api.FlowEngine;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class UpgradeApplicationFlowFactoryTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("test-flow-engine", false);

  @InjectMocks private UpgradeApplicationFlowFactory flowFactory;

  @Mock private ApplicationDiscoveryLoader applicationDiscoveryLoader;
  @Mock private ApplicationDependencyUpdater applicationDependencyUpdater;
  @Mock private UpgradeRequestDependencyValidator upgradeRequestDependencyValidator;

  @Mock private ModulesFlowProvider modulesFlowProvider;
  @Mock private ApplicationFlowInitializer flowInitializer;
  @Mock private FailedApplicationFlowFinalizer failedFlowFinalizer;
  @Mock private UpgradeApplicationFlowFinalizer finishedFlowFinalizer;
  @Mock private SkippedApplicationFlowFinalizer skippedFlowFinalizer;

  @Test
  void prepareFlow() {
    mockStageNames(applicationDependencyUpdater, upgradeRequestDependencyValidator, flowInitializer,
      failedFlowFinalizer, finishedFlowFinalizer, skippedFlowFinalizer, applicationDiscoveryLoader);

    var request = EntitlementRequest.builder().type(UPGRADE).tenantId(TENANT_ID).ignoreErrors(true).build();
    var entitledApplicationDescriptor = applicationDescriptor("app-foo-1.0.0");
    var flowParameters = flowParameters(request, applicationDescriptor("app-foo-1.1.0"), entitledApplicationDescriptor);

    var actual = flowFactory.createFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(actual);

    var context = appStageContext(actual.getId(), flowParameters, emptyMap());

    var inOrder = Mockito.inOrder(applicationDependencyUpdater, upgradeRequestDependencyValidator,
      flowInitializer, failedFlowFinalizer, finishedFlowFinalizer, skippedFlowFinalizer, modulesFlowProvider,
      applicationDiscoveryLoader);

    inOrder.verify(modulesFlowProvider).getName();
    verifyStageExecution(inOrder, flowInitializer, context);
    verifyStageExecution(inOrder, upgradeRequestDependencyValidator, context);
    verifyStageExecution(inOrder, applicationDiscoveryLoader, context);
    inOrder.verify(modulesFlowProvider).createFlow(context);
    verifyStageExecution(inOrder, applicationDependencyUpdater, context);
    verifyStageExecution(inOrder, finishedFlowFinalizer, context);
  }

  private static <T extends IdentifiableStageContext> void verifyStageExecution(InOrder inOrder,
    DatabaseLoggingStage<T> stage, T context) {
    inOrder.verify(stage).onStart(context);
    inOrder.verify(stage).execute(context);
    inOrder.verify(stage).onSuccess(context);
  }
}
