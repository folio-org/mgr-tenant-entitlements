package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLED;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CancelledFlowFinalizerTest {

  @InjectMocks private CancelledFlowFinalizer flowFinalizer;

  @Mock private FlowRepository flowRepository;
  @Mock private ApplicationFlowService applicationFlowService;
  @Captor private ArgumentCaptor<FlowEntity> flowEntityCaptor;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_entitle_positive() {
    var entity = new FlowEntity();
    entity.setId(FLOW_ID);
    entity.setTenantId(TENANT_ID);

    when(flowRepository.getReferenceById(FLOW_ID)).thenReturn(entity);
    when(flowRepository.save(flowEntityCaptor.capture())).thenReturn(entity);

    var stageContext = commonStageContext(FLOW_ID, flowParameters(), Map.of());
    flowFinalizer.execute(stageContext);

    var capturedValue = flowEntityCaptor.getValue();
    assertThat(capturedValue.getStatus()).isEqualTo(CANCELLED);
    verify(applicationFlowService).removeAllQueuedFlows(FLOW_ID);
  }

  private static Map<?, ?> flowParameters() {
    var entitlementRequest = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    return Map.of(PARAM_REQUEST, entitlementRequest);
  }
}
