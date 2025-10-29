package org.folio.entitlement.service;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.QUEUED;
import static org.folio.entitlement.service.ApplicationFlowServiceTest.TestValues.applicationFlow;
import static org.folio.entitlement.service.ApplicationFlowServiceTest.TestValues.applicationFlowEntity;
import static org.folio.entitlement.service.ApplicationFlowServiceTest.TestValues.entitlementRequest;
import static org.folio.entitlement.service.ApplicationFlowServiceTest.TestValues.flowStage;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_NAME;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.applicationDependency;
import static org.folio.entitlement.support.TestValues.applicationDependencyEntity;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.common.domain.model.SearchResult;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.entity.type.EntityEntitlementType;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.mapper.ApplicationFlowMapper;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.service.flow.ApplicationFlowService;
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
class ApplicationFlowServiceTest {

  @InjectMocks private ApplicationFlowService applicationFlowService;

  @Mock private FlowStageService flowStageService;
  @Mock private ApplicationFlowMapper mapper;
  @Mock private ApplicationFlowRepository repository;
  @Mock private ApplicationDependencyService dependencyService;

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
      var entity = applicationFlowEntity(FINISHED);
      var offsetRequest = OffsetRequest.of(0, 100);
      var applicationFlow = applicationFlow(FINISHED);

      when(repository.findByCql(query, offsetRequest)).thenReturn(new PageImpl<>(List.of(entity)));
      when(mapper.map(entity)).thenReturn(applicationFlow);

      var result = applicationFlowService.find(query, 100, 0);

      assertThat(result).isEqualTo(SearchResult.of(List.of(applicationFlow)));
    }
  }

  @Nested
  @DisplayName("getById")
  class GetById {

    @Test
    void positive_includeStagesFalse() {
      var entity = applicationFlowEntity();
      var applicationFlow = applicationFlow();
      when(repository.getReferenceById(APPLICATION_FLOW_ID)).thenReturn(entity);
      when(mapper.map(entity)).thenReturn(applicationFlow);

      var result = applicationFlowService.getById(APPLICATION_FLOW_ID, false);

      assertThat(result).isEqualTo(applicationFlow);
    }

    @Test
    void positive_includeStagesTrue() {
      var entity = applicationFlowEntity();
      var applicationFlow = applicationFlow();
      var stage = flowStage("test-stage");
      var stages = List.of(stage);

      when(repository.getReferenceById(APPLICATION_FLOW_ID)).thenReturn(entity);
      when(mapper.map(entity)).thenReturn(applicationFlow);
      when(flowStageService.findByFlowId(APPLICATION_FLOW_ID)).thenReturn(SearchResult.of(stages));

      var result = applicationFlowService.getById(APPLICATION_FLOW_ID, true);

      assertThat(result).isEqualTo(applicationFlow);
      assertThat(result.getStages()).isEqualTo(stages);
    }

    @Test
    void positive_includeStagesTrueAndEmptyStagesList() {
      var entity = applicationFlowEntity();
      var applicationFlow = applicationFlow();

      when(repository.getReferenceById(APPLICATION_FLOW_ID)).thenReturn(entity);
      when(mapper.map(entity)).thenReturn(applicationFlow);
      when(flowStageService.findByFlowId(APPLICATION_FLOW_ID)).thenReturn(SearchResult.empty());

      var result = applicationFlowService.getById(APPLICATION_FLOW_ID, true);

      assertThat(result).isEqualTo(applicationFlow);
      assertThat(result.getStages()).isEmpty();
    }

    @Test
    void negative_emptyResult() {
      when(repository.getReferenceById(APPLICATION_FLOW_ID)).thenThrow(EntityNotFoundException.class);
      assertThatThrownBy(() -> applicationFlowService.getById(APPLICATION_FLOW_ID, true))
        .isInstanceOf(EntityNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("findLastFlows")
  class FindLastFlows {

    @Test
    void positive() {
      var applicationIds = List.of(APPLICATION_ID);
      var entity = applicationFlowEntity();
      var applicationFlow = applicationFlow();

      when(mapper.map(entity)).thenReturn(applicationFlow);
      when(repository.findLastFlows(applicationIds, TENANT_ID)).thenReturn(List.of(entity));

      var result = applicationFlowService.findLastFlows(applicationIds, TENANT_ID);

      assertThat(result).isEqualTo(List.of(applicationFlow));
    }

    @Test
    void findLastFlows_positive_emptyListOfApplicationIds() {
      var result = applicationFlowService.findLastFlows(emptyList(), TENANT_ID);
      assertThat(result).isEmpty();
      verifyNoInteractions(mapper, repository);
    }
  }

  @Nested
  @DisplayName("findLastDependentFlows")
  class FindLastDependentFlows {

    @Test
    void positive() {
      var entity = applicationFlowEntity();
      var applicationFlow = applicationFlow();
      var dependency = applicationDependency("test-app", "0.0.1");
      var dependencyEntity = applicationDependencyEntity(dependency);
      var appId = "test-app-0.0.1";

      var dependencyEntities = List.of(dependencyEntity);
      when(dependencyService.findAllByParentApplicationName(TENANT_ID, appId)).thenReturn(dependencyEntities);
      when(repository.findLastFlows(List.of(APPLICATION_ID), TENANT_ID)).thenReturn(List.of(entity));
      when(mapper.map(entity)).thenReturn(applicationFlow);

      var result = applicationFlowService.findLastDependentFlows(appId, TENANT_ID);

      assertThat(result).isEqualTo(List.of(applicationFlow));
    }
  }

  @Nested
  @DisplayName("findByFlowId")
  class FindByFlowId {

    @Test
    void positive_includeStagesFalse() {
      var entity = applicationFlowEntity();
      var applicationFlow = applicationFlow();

      when(repository.findByFlowId(FLOW_ID)).thenReturn(List.of(entity));
      when(mapper.map(entity)).thenReturn(applicationFlow);

      var result = applicationFlowService.findByFlowId(FLOW_ID, false);

      assertThat(result).containsExactly(applicationFlow);
    }

    @Test
    void positive_includeStagesTrue() {
      var entity = applicationFlowEntity();
      var applicationFlow = applicationFlow();
      var flowStages = List.of(flowStage("bar-stage"));
      var flowStagesMap = Map.of(APPLICATION_FLOW_ID, flowStages);

      when(repository.findByFlowId(FLOW_ID)).thenReturn(List.of(entity));
      when(mapper.map(entity)).thenReturn(applicationFlow);
      when(flowStageService.findByFlowIds(List.of(APPLICATION_FLOW_ID))).thenReturn(flowStagesMap);

      var result = applicationFlowService.findByFlowId(FLOW_ID, true);

      assertThat(result).containsExactly(applicationFlow().stages(flowStages));
    }
  }

  @Nested
  @DisplayName("findByFlowIds")
  class FindByFlowIds {

    @Test
    void positive() {
      var entity = applicationFlowEntity();
      var applicationFlow = applicationFlow();
      var flowIds = List.of(FLOW_ID);

      when(repository.findByFlowIds(flowIds)).thenReturn(List.of(entity));
      when(mapper.map(entity)).thenReturn(applicationFlow);

      var result = applicationFlowService.findByFlowIds(flowIds);

      assertThat(result).isEqualTo(Map.of(FLOW_ID, List.of(applicationFlow)));
    }

    @Test
    void positive_emptyInput() {
      var result = applicationFlowService.findByFlowIds(emptyList());

      assertThat(result).isEmpty();
      verifyNoInteractions(repository, mapper);
    }
  }

  @Nested
  @DisplayName("findLastFlowsByNames")
  class FindLastFlowsByNames {

    @Test
    void positive() {
      var entity = applicationFlowEntity();
      var applicationFlow = applicationFlow();
      var applicationNames = List.of(APPLICATION_NAME);

      when(repository.findLastFlowsByApplicationNames(applicationNames, TENANT_ID)).thenReturn(List.of(entity));
      when(mapper.map(entity)).thenReturn(applicationFlow);

      var result = applicationFlowService.findLastFlowsByNames(applicationNames, TENANT_ID);

      assertThat(result).containsExactly(applicationFlow);
    }

    @Test
    void positive_emptyInput() {
      var result = applicationFlowService.findLastFlowsByNames(emptyList(), TENANT_ID);

      assertThat(result).isEmpty();
      verifyNoInteractions(repository, mapper);
    }
  }

  @Nested
  @DisplayName("createQueuedApplicationFlow")
  class CreateQueuedApplicationFlow {

    @Test
    void execute_positive() {
      var entity = applicationFlowEntity(QUEUED);
      var entities = List.of(entity);
      var applicationFlow = applicationFlow(QUEUED);

      when(mapper.mapWithStatusQueued(TENANT_ID, APPLICATION_ID, FLOW_ID, ENTITLE)).thenReturn(entity);
      when(repository.saveAll(entities)).thenReturn(entities);
      when(mapper.map(entity)).thenReturn(applicationFlow);

      var request = entitlementRequest(ENTITLE, APPLICATION_ID);
      var result = applicationFlowService.createQueuedApplicationFlows(FLOW_ID, request);

      assertThat(result).containsExactly(applicationFlow);
    }
  }

  static class TestValues {

    static EntitlementRequest entitlementRequest(EntitlementType type, String... applicationIds) {
      return EntitlementRequest.builder()
        .type(type)
        .ignoreErrors(true)
        .applications(List.of(applicationIds))
        .tenantId(TENANT_ID)
        .build();
    }

    static ApplicationFlowEntity applicationFlowEntity() {
      return applicationFlowEntity(null, null);
    }

    static ApplicationFlowEntity applicationFlowEntity(ExecutionStatus status) {
      var entity = applicationFlowEntity(null, null);
      entity.setStatus(EntityExecutionStatus.from(status));
      return entity;
    }

    static ApplicationFlowEntity applicationFlowEntity(ZonedDateTime startedAt, ZonedDateTime finishedAt) {
      var entity = new ApplicationFlowEntity();
      entity.setId(APPLICATION_FLOW_ID);
      entity.setFlowId(FLOW_ID);
      entity.setApplicationId(APPLICATION_ID);
      entity.setTenantId(TENANT_ID);
      entity.setType(EntityEntitlementType.ENTITLE);
      entity.setStatus(EntityExecutionStatus.FINISHED);
      entity.setStartedAt(startedAt);
      entity.setFinishedAt(finishedAt);
      return entity;
    }

    static ApplicationFlow applicationFlow() {
      return applicationFlow(FINISHED, null, null);
    }

    static ApplicationFlow applicationFlow(ExecutionStatus status) {
      return applicationFlow(status, null, null);
    }

    static ApplicationFlow applicationFlow(ExecutionStatus status, Date startedAt, Date finishedAt) {
      return new ApplicationFlow()
        .id(APPLICATION_FLOW_ID)
        .flowId(FLOW_ID)
        .applicationId(APPLICATION_ID)
        .tenantId(TENANT_ID)
        .type(ENTITLE)
        .status(status)
        .startedAt(startedAt)
        .finishedAt(finishedAt);
    }

    static FlowStage flowStage(String stageName) {
      return new FlowStage()
        .flowId(APPLICATION_FLOW_ID)
        .name(stageName)
        .status(FINISHED);
    }
  }
}
