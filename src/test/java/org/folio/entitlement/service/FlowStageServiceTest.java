package org.folio.entitlement.service;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.SearchResult;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.domain.entity.FlowStageEntity;
import org.folio.entitlement.domain.entity.key.FlowStageKey;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.mapper.FlowStageMapper;
import org.folio.entitlement.repository.FlowStageRepository;
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
class FlowStageServiceTest {

  private static final String STAGE_NAME = "test-stage";

  @InjectMocks private FlowStageService flowStageService;
  @Mock private FlowStageMapper flowStageMapper;
  @Mock private FlowStageRepository flowStageRepository;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void findByFlowId_positive() {
    var entity = flowStageEntity();
    var entitlementStage = flowStage();

    when(flowStageRepository.findByFlowId(APPLICATION_FLOW_ID)).thenReturn(List.of(entity));
    when(flowStageMapper.map(entity)).thenReturn(entitlementStage);

    var result = flowStageService.findByFlowId(APPLICATION_FLOW_ID);

    assertThat(result).isEqualTo(SearchResult.of(List.of(entitlementStage)));
  }

  @Test
  void getFlowStage_positive() {
    var entity = flowStageEntity();
    var entitlementStage = flowStage();
    var entityId = FlowStageKey.of(APPLICATION_FLOW_ID, STAGE_NAME);

    when(flowStageRepository.getReferenceById(entityId)).thenReturn(entity);
    when(flowStageMapper.map(entity)).thenReturn(entitlementStage);

    var result = flowStageService.getEntitlementStage(APPLICATION_FLOW_ID, STAGE_NAME);

    assertThat(result).isEqualTo(entitlementStage);
  }

  @Test
  void findFlowStagesForByFlow_positive() {
    var entity = flowStageEntity();
    var flowStage = flowStage();

    when(flowStageRepository.findByFlowId(FLOW_ID)).thenReturn(List.of(entity));
    when(flowStageMapper.map(entity)).thenReturn(flowStage);

    var result = flowStageService.findStagesForFlow(FLOW_ID);

    assertThat(result).isEqualTo(Map.of(APPLICATION_FLOW_ID, List.of(flowStage)));
  }

  @Test
  void findByFlowIds_positive() {
    var entity = flowStageEntity();
    var flowStage = flowStage();
    var flowIds = List.of(APPLICATION_FLOW_ID);

    when(flowStageRepository.findByFlowIds(flowIds)).thenReturn(List.of(entity));
    when(flowStageMapper.map(entity)).thenReturn(flowStage);

    var result = flowStageService.findByFlowIds(flowIds);

    assertThat(result).isEqualTo(Map.of(APPLICATION_FLOW_ID, List.of(flowStage)));
  }

  @Test
  void findByFlowIds_positive_emptyInput() {
    var result = flowStageService.findByFlowIds(emptyList());

    assertThat(result).isEmpty();
    verifyNoInteractions(flowStageMapper, flowStageRepository);
  }

  private static FlowStage flowStage() {
    return new FlowStage()
      .name(STAGE_NAME)
      .flowId(APPLICATION_FLOW_ID)
      .status(FINISHED);
  }

  private static FlowStageEntity flowStageEntity() {
    var entity = new FlowStageEntity();
    entity.setStageName(STAGE_NAME);
    entity.setFlowId(APPLICATION_FLOW_ID);
    entity.setStatus(EntityExecutionStatus.FINISHED);
    return entity;
  }
}
