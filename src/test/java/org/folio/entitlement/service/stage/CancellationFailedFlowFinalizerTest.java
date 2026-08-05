package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLATION_FAILED;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.EnumSet;
import java.util.Map;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.flow.ApplicationFlowService;
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
class CancellationFailedFlowFinalizerTest {

  @InjectMocks private CancellationFailedFlowFinalizer flowFinalizer;

  @Mock private FlowRepository flowRepository;
  @Mock private ApplicationFlowService applicationFlowService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_entitle_positive() {
    when(flowRepository.updateStatusIfCurrentIn(
      eq(FLOW_ID), eq(CANCELLATION_FAILED), eq(EnumSet.allOf(EntityExecutionStatus.class)), any(ZonedDateTime.class)))
      .thenReturn(1);

    var stageContext = commonStageContext(FLOW_ID, flowParameters(), Map.of());
    flowFinalizer.execute(stageContext);

    verify(applicationFlowService).removeAllQueuedFlows(FLOW_ID);
  }

  private static Map<?, ?> flowParameters() {
    var entitlementRequest = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    return Map.of(PARAM_REQUEST, entitlementRequest);
  }
}
