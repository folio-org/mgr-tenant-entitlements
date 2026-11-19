package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.PLATFORM_ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.EntitlementFlowInProgressException;
import org.folio.entitlement.service.flow.FlowService;
import org.folio.entitlement.service.lock.PlatformLockService;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FlowInitializerTest {

  @InjectMocks private FlowInitializer flowInitializer;

  @Mock private FlowService flowService;
  @Mock private PlatformLockService platformLockService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive_tenantFlowChecksPlatformLockAfterFlowIsCommitted() {
    var request = EntitlementRequest.builder()
      .type(ENTITLE).tenantId(TENANT_ID).applications(List.of(APPLICATION_ID)).build();

    flowInitializer.execute(commonStageContext(FLOW_ID.toString(), Map.of(PARAM_REQUEST, request), Map.of()));

    var flowCaptor = ArgumentCaptor.forClass(Flow.class);
    var inOrder = inOrder(flowService, platformLockService);
    // flowService.create is transactional: when it returns, the IN_PROGRESS flow is already committed
    inOrder.verify(flowService).create(flowCaptor.capture());
    inOrder.verify(platformLockService).assertNotHeld();

    var flow = flowCaptor.getValue();
    assertThat(flow.getId()).isEqualTo(FLOW_ID);
    assertThat(flow.getTenantId()).isEqualTo(TENANT_ID);
    assertThat(flow.getStatus()).isEqualTo(ExecutionStatus.IN_PROGRESS);
  }

  @Test
  void execute_negative_platformOperationHoldsLockAfterFlowIsCommitted() {
    var request = EntitlementRequest.builder()
      .type(ENTITLE).tenantId(TENANT_ID).applications(List.of(APPLICATION_ID)).build();
    var exception = new EntitlementFlowInProgressException("platform busy", 5);
    doThrow(exception).when(platformLockService).assertNotHeld();
    var context = commonStageContext(FLOW_ID.toString(), Map.of(PARAM_REQUEST, request), Map.of());

    assertThatThrownBy(() -> flowInitializer.execute(context)).isSameAs(exception);

    verify(flowService).create(any(Flow.class));
  }

  @Test
  void execute_positive_platformFlowDoesNotCheckPlatformLock() {
    var request = EntitlementRequest.builder()
      .type(PLATFORM_ENTITLE).applications(List.of(APPLICATION_ID)).build();

    flowInitializer.execute(commonStageContext(FLOW_ID.toString(), Map.of(PARAM_REQUEST, request), Map.of()));

    verify(flowService).create(any(Flow.class));
  }
}
