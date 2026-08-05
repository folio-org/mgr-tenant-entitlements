package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.stage.ApplicationFlowInitializer;
import org.folio.entitlement.service.stage.CancellationFailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.CancelledApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.FailedApplicationFlowFinalizer;
import org.folio.entitlement.service.stage.SkippedApplicationFlowFinalizer;
import org.folio.flow.api.Flow;
import org.folio.flow.api.FlowEngine;
import org.folio.flow.exception.FlowExecutionException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationFlowFinalizerCallbacksTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("test-flow-engine", false);

  @Mock private ApplicationFlowInitializer mainStage;
  @Mock private SkippedApplicationFlowFinalizer skippedFlowFinalizer;
  @Mock private FailedApplicationFlowFinalizer failedFlowFinalizer;
  @Mock private CancelledApplicationFlowFinalizer cancelledFlowFinalizer;
  @Mock private CancellationFailedApplicationFlowFinalizer cancellationFailedFlowFinalizer;

  @Test
  void apply_negative_failedFinalizerExecutedOnFlowError() {
    mockStageNames(mainStage, skippedFlowFinalizer, failedFlowFinalizer,
      cancelledFlowFinalizer, cancellationFailedFlowFinalizer);
    var exception = new RuntimeException("Stage failed");
    doThrow(exception).when(mainStage).execute(any(ApplicationStageContext.class));

    var finalizerCallbacks = new ApplicationFlowFinalizerCallbacks(skippedFlowFinalizer, failedFlowFinalizer,
      cancelledFlowFinalizer, cancellationFailedFlowFinalizer);
    var flow = finalizerCallbacks.apply(Flow.builder().id(FLOW_STAGE_ID).stage(mainStage))
      .executionStrategy(IGNORE_ON_ERROR)
      .build();

    assertThatThrownBy(() -> flowEngine.execute(flow)).isInstanceOf(FlowExecutionException.class);

    var context = appStageContext(flow.getId(), flow.getFlowParameters(), emptyMap());
    verify(mainStage).onStart(context);
    verify(mainStage).execute(context);
    verify(mainStage).onError(context, exception);

    verify(failedFlowFinalizer).onStart(context);
    verify(failedFlowFinalizer).execute(context);
    verify(failedFlowFinalizer).onSuccess(context);

    verify(skippedFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
    verify(cancelledFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
    verify(cancellationFailedFlowFinalizer, never()).execute(any(ApplicationStageContext.class));
  }
}
