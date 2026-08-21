package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.NON_TERMINAL_STATUSES;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Map;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FinishedFlowFinalizerTest {

  @InjectMocks private FinishedFlowFinalizer flowFinalizer;

  @Mock private FlowFinalizerStatusProvider<CommonStageContext> statusProvider;
  @Mock private FlowRepository flowRepository;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_entitle_positive() {
    when(statusProvider.getFinalStatus(any())).thenReturn(ExecutionStatus.FINISHED);
    when(flowRepository.updateStatusIfCurrentIn(
      eq(FLOW_ID), eq(FINISHED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class))).thenReturn(1);

    var stageContext = commonStageContext(FLOW_ID, flowParameters(), Map.of());
    flowFinalizer.execute(stageContext);

    verify(flowRepository).updateStatusIfCurrentIn(
      eq(FLOW_ID), eq(FINISHED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class));
  }

  @Test
  void execute_positive_flowAlreadyTerminal() {
    when(statusProvider.getFinalStatus(any())).thenReturn(ExecutionStatus.FINISHED);
    when(flowRepository.updateStatusIfCurrentIn(
      eq(FLOW_ID), eq(FINISHED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class))).thenReturn(0);

    var stageContext = commonStageContext(FLOW_ID, flowParameters(), Map.of());
    flowFinalizer.execute(stageContext);

    verify(flowRepository).updateStatusIfCurrentIn(
      eq(FLOW_ID), eq(FINISHED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class));
  }

  @Test
  void execute_positive_inProgressStatus_updateNotCalled() {
    when(statusProvider.getFinalStatus(any())).thenReturn(ExecutionStatus.IN_PROGRESS);

    var stageContext = commonStageContext(FLOW_ID, flowParameters(), Map.of());
    flowFinalizer.execute(stageContext);

    verify(flowRepository, never()).updateStatusIfCurrentIn(any(), any(), any(), any());
  }

  private static Map<?, ?> flowParameters() {
    var entitlementRequest = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    return Map.of(PARAM_REQUEST, entitlementRequest);
  }
}
