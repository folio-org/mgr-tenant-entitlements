package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyMap;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
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

  @Mock private ModulesFlowProvider modulesFlowProvider;
  @Mock private ApplicationFlowInitializer flowInitializer;
  @Mock private FailedApplicationFlowFinalizer failedFlowFinalizer;
  @Mock private SkippedApplicationFlowFinalizer skippedFlowFinalizer;
  @Mock private EntitleApplicationFlowFinalizer finishedFlowFinalizer;
  @Mock private CancelledApplicationFlowFinalizer cancelledFlowFinalizer;
  @Mock private CancellationFailedApplicationFlowFinalizer cancellationFailedApplicationFlowFinalizer;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void prepareFlow_positive() {
    mockStageNames(applicationDescriptorValidator, applicationDependencySaver,
      entitleRequestDependencyValidator, applicationDiscoveryLoader,
      finishedFlowFinalizer, flowInitializer, failedFlowFinalizer, cancelledFlowFinalizer,
      cancellationFailedApplicationFlowFinalizer, skippedFlowFinalizer);

    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = TestValues.flowParameters(request, TestValues.appDescriptor());

    var actual = flowFactory.createFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(actual);

    var context = appStageContext(actual.getId(), flowParameters, emptyMap());

    var inOrder = Mockito.inOrder(flowInitializer, applicationDependencySaver, entitleRequestDependencyValidator,
      applicationDiscoveryLoader, finishedFlowFinalizer, applicationDescriptorValidator, modulesFlowProvider);

    inOrder.verify(modulesFlowProvider).getName();
    verifyStageExecution(inOrder, flowInitializer, context);
    verifyStageExecution(inOrder, applicationDescriptorValidator, context);
    verifyStageExecution(inOrder, applicationDependencySaver, context);
    verifyStageExecution(inOrder, entitleRequestDependencyValidator, context);
    verifyStageExecution(inOrder, applicationDiscoveryLoader, context);
    inOrder.verify(modulesFlowProvider).createFlow(context);
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
