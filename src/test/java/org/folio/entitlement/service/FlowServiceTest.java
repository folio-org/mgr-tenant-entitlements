package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.service.FlowServiceTest.TestValues.applicationFlow;
import static org.folio.entitlement.service.FlowServiceTest.TestValues.flow;
import static org.folio.entitlement.service.FlowServiceTest.TestValues.flowEntity;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.common.domain.model.SearchResult;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.domain.entity.type.EntityEntitlementType;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.mapper.FlowMapper;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.folio.entitlement.service.flow.FlowService;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FlowServiceTest {

  @InjectMocks private FlowService flowService;

  @Mock private FlowMapper flowMapper;
  @Mock private FlowRepository flowRepository;
  @Mock private FlowStageService flowStageService;
  @Mock private ApplicationFlowService applicationFlowService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Nested
  @DisplayName("find")
  class Find {

    @Test
    void positive() {
      var query = "tenantId = " + TENANT_ID;
      var flow = flow();
      var flowEntity = flowEntity();
      var offsetRequest = OffsetRequest.of(0, 100);
      var applicationFlow = applicationFlow();
      var applicationFlowsMap = Map.of(APPLICATION_FLOW_ID, List.of(applicationFlow));

      when(flowRepository.findByCql(query, offsetRequest)).thenReturn(new PageImpl<>(List.of(flowEntity)));
      when(applicationFlowService.findByFlowIds(List.of(FLOW_ID))).thenReturn(applicationFlowsMap);
      when(flowMapper.map(flowEntity)).thenReturn(flow);

      var result = flowService.find(query, 100, 0);
      assertThat(result).isEqualTo(SearchResult.of(List.of(flow)));
    }
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    void negative_emptyResult() {
      when(flowRepository.getReferenceById(FLOW_ID)).thenThrow(EntityNotFoundException.class);
      assertThatThrownBy(() -> flowService.getById(FLOW_ID, false))
        .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void positive_includeStagesIsFalse() {
      var finishedAt = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"));
      var startedAt = finishedAt.minusSeconds(1);
      var flow = flow(FINISHED, Date.from(startedAt.toInstant()), Date.from(finishedAt.toInstant()));
      var flowEntity = flowEntity(startedAt, finishedAt);
      var applicationFlows = List.of(applicationFlow());

      when(flowRepository.getReferenceById(FLOW_ID)).thenReturn(flowEntity);
      when(flowMapper.map(flowEntity)).thenReturn(flow);
      when(applicationFlowService.findByFlowId(FLOW_ID, false)).thenReturn(applicationFlows);

      var result = flowService.getById(FLOW_ID, false);

      assertThat(result).isEqualTo(new Flow()
        .id(FLOW_ID)
        .applicationFlows(applicationFlows)
        .startedAt(Date.from(startedAt.toInstant()))
        .finishedAt(Date.from(finishedAt.toInstant()))
        .status(FINISHED)
        .type(ENTITLE));
    }

    @Test
    void positive_includeStagesIsTrue() {
      var finishedAt = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"));
      var startedAt = finishedAt.minusSeconds(1);
      var flow = flow(FINISHED, Date.from(startedAt.toInstant()), Date.from(finishedAt.toInstant()));
      var flowEntity = flowEntity(startedAt, finishedAt);
      var flowStages = List.of(stage(FLOW_ID, "flow-stage"));
      var applicationFlows = List.of(applicationFlow().addStagesItem(stage(APPLICATION_FLOW_ID, "app-flow-stage")));

      when(flowRepository.getReferenceById(FLOW_ID)).thenReturn(flowEntity);
      when(flowMapper.map(flowEntity)).thenReturn(flow);
      when(applicationFlowService.findByFlowId(FLOW_ID, true)).thenReturn(applicationFlows);
      when(flowStageService.findByFlowId(FLOW_ID)).thenReturn(SearchResult.of(flowStages));

      var result = flowService.getById(FLOW_ID, true);

      assertThat(result).isEqualTo(new Flow()
        .id(FLOW_ID)
        .applicationFlows(applicationFlows)
        .startedAt(Date.from(startedAt.toInstant()))
        .finishedAt(Date.from(finishedAt.toInstant()))
        .stages(flowStages)
        .status(FINISHED)
        .type(ENTITLE));
    }

    private FlowStage stage(UUID flowId, String stageName) {
      return new FlowStage()
        .flowId(flowId)
        .name(stageName)
        .status(FINISHED);
    }
  }

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    void positive() {
      var flow = flow();
      var flowEntity = flowEntity();

      when(flowMapper.map(flow)).thenReturn(flowEntity);
      when(flowRepository.save(flowEntity)).thenReturn(flowEntity);
      when(flowMapper.map(flowEntity)).thenReturn(flow);

      var result = flowService.create(flow);

      assertThat(result).isEqualTo(flow);
    }
  }

  static class TestValues {

    static FlowEntity flowEntity() {
      return flowEntity(null, null);
    }

    static FlowEntity flowEntity(ZonedDateTime startedAt, ZonedDateTime finishedAt) {
      var entity = new FlowEntity();
      entity.setId(FLOW_ID);
      entity.setType(EntityEntitlementType.ENTITLE);
      entity.setStatus(EntityExecutionStatus.FINISHED);
      entity.setStartedAt(startedAt);
      entity.setFinishedAt(finishedAt);
      return entity;
    }

    static Flow flow() {
      return flow(FINISHED, null, null);
    }

    static Flow flow(ExecutionStatus status, Date startedAt, Date finishedAt) {
      return new Flow()
        .id(FLOW_ID)
        .type(ENTITLE)
        .status(status)
        .startedAt(startedAt)
        .finishedAt(finishedAt);
    }

    static ApplicationFlow applicationFlow() {
      return new ApplicationFlow()
        .id(FLOW_ID)
        .type(ENTITLE)
        .applicationId(APPLICATION_ID)
        .tenantId(TENANT_ID)
        .status(FINISHED);
    }
  }
}
