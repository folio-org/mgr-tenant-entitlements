package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.model.IdentifiableStageContext.PARAM_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.STAGE_UUID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.domain.model.CommonStageContext;
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
class FlowFinalizerStageAwareStatusProviderTest {

  @InjectMocks
  private FlowFinalizerStageAwareStatusProvider<FlowEntity, CommonStageContext> statusProvider;
  @Mock private FlowRepository flowRepository;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void getFinalStatus_positive_noInProgressStages_returnsFinished() {
    when(flowRepository.existsAnyStageByFlowIdAndStatusExcluding(FLOW_ID, IN_PROGRESS, STAGE_UUID)).thenReturn(false);

    var ctx = commonStageContext(FLOW_ID, Map.of(), Map.of(PARAM_STAGE_ID, STAGE_UUID));
    var result = statusProvider.getFinalStatus(ctx);

    assertThat(result).isEqualTo(ExecutionStatus.FINISHED);
  }

  @Test
  void getFinalStatus_positive_hasInProgressStages_returnsInProgress() {
    when(flowRepository.existsAnyStageByFlowIdAndStatusExcluding(FLOW_ID, IN_PROGRESS, STAGE_UUID)).thenReturn(true);

    var ctx = commonStageContext(FLOW_ID, Map.of(), Map.of(PARAM_STAGE_ID, STAGE_UUID));
    var result = statusProvider.getFinalStatus(ctx);

    assertThat(result).isEqualTo(ExecutionStatus.IN_PROGRESS);
  }
}
