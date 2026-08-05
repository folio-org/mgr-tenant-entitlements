package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.service.stage.CancellationFailedFlowFinalizer;
import org.folio.entitlement.service.stage.CancelledFlowFinalizer;
import org.folio.entitlement.service.stage.FailedFlowFinalizer;
import org.folio.entitlement.service.stage.FlowInitializer;
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
class FlowFinalizerCallbacksTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("test-flow-engine", false);

  @Mock private FlowInitializer mainStage;
  @Mock private FailedFlowFinalizer failedFlowFinalizer;
  @Mock private CancelledFlowFinalizer cancelledFlowFinalizer;
  @Mock private CancellationFailedFlowFinalizer cancellationFailedFlowFinalizer;

  @Test
  void apply_negative_failedFinalizerExecutedOnFlowError() {
    mockStageNames(mainStage, failedFlowFinalizer, cancelledFlowFinalizer, cancellationFailedFlowFinalizer);
    var exception = new RuntimeException("Stage failed");
    doThrow(exception).when(mainStage).execute(any(CommonStageContext.class));

    var finalizerCallbacks =
      new FlowFinalizerCallbacks(failedFlowFinalizer, cancelledFlowFinalizer, cancellationFailedFlowFinalizer);
    var flow = finalizerCallbacks.apply(Flow.builder().id(FLOW_ID).stage(mainStage))
      .executionStrategy(IGNORE_ON_ERROR)
      .build();

    assertThatThrownBy(() -> flowEngine.execute(flow)).isInstanceOf(FlowExecutionException.class);

    var context = commonStageContext(flow.getId(), flow.getFlowParameters(), emptyMap());
    verify(mainStage).onStart(context);
    verify(mainStage).execute(context);
    verify(mainStage).onError(context, exception);

    verify(failedFlowFinalizer).onStart(context);
    verify(failedFlowFinalizer).execute(context);
    verify(failedFlowFinalizer).onSuccess(context);

    verify(cancelledFlowFinalizer, never()).execute(any(CommonStageContext.class));
    verify(cancellationFailedFlowFinalizer, never()).execute(any(CommonStageContext.class));
  }
}
