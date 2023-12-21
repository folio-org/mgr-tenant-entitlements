package org.folio.entitlement.service;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.QUEUED;
import static org.folio.entitlement.service.EntitlementFlowServiceTest.TestValues.applicationFlow;
import static org.folio.entitlement.service.EntitlementFlowServiceTest.TestValues.entitlementFlowEntity;
import static org.folio.entitlement.service.EntitlementFlowServiceTest.TestValues.entitlementRequest;
import static org.folio.entitlement.service.EntitlementFlowServiceTest.TestValues.fullApplicationFlowId;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.applicationDependency;
import static org.folio.entitlement.support.TestValues.applicationDependencyEntity;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.dependency;
import static org.folio.entitlement.support.TestValues.extendedEntitlement;
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.folio.flow.model.FlowExecutionStrategy.CANCEL_ON_ERROR;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.common.domain.model.SearchResult;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementFlow;
import org.folio.entitlement.domain.dto.EntitlementStage;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.EntitlementFlowEntity;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.mapper.EntitlementFlowMapper;
import org.folio.entitlement.repository.EntitlementFlowRepository;
import org.folio.entitlement.service.flow.EntitlementFlowProvider;
import org.folio.entitlement.service.flow.EntitlementFlowService;
import org.folio.entitlement.service.flow.RevokeFlowProvider;
import org.folio.flow.api.Flow;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementFlowServiceTest {

  @InjectMocks private EntitlementFlowService entitlementFlowService;

  @Mock private EntitlementFlowProvider flowProvider;
  @Mock private RevokeFlowProvider revokeFlowProvider;
  @Mock private EntitlementFlowMapper entitlementFlowMapper;
  @Mock private EntitlementStageService entitlementStageService;
  @Mock private EntitlementFlowRepository entitlementFlowRepository;
  @Mock private ApplicationDependencyService dependencyService;
  @Mock private ApplicationManagerService appManagerService;
  @Mock private Flow flow;

  @Nested
  @DisplayName("find")
  class Find {

    @Test
    void positive() {
      var query = "tenantId = " + TENANT_ID;
      var entity = entitlementFlowEntity(FLOW_ID);
      var offsetRequest = OffsetRequest.of(0, 100);
      var applicationFlow = applicationFlow(FLOW_ID);

      when(entitlementFlowRepository.findByCql(query, offsetRequest)).thenReturn(new PageImpl<>(List.of(entity)));
      when(entitlementFlowMapper.map(entity)).thenReturn(applicationFlow);

      var result = entitlementFlowService.find(query, 100, 0);
      assertThat(result).isEqualTo(SearchResult.of(List.of(applicationFlow)));
    }
  }

  @Nested
  @DisplayName("findById")
  class FindById {

    @Test
    void negative_emptyResult() {
      when(entitlementFlowRepository.findByFlowId(FLOW_ID)).thenReturn(emptyList());
      assertThatThrownBy(() -> entitlementFlowService.findById(FLOW_ID, false))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage("Application flows are not found for flow id: " + FLOW_ID);
    }

    @Test
    void positive_includeStagesIsFalse() {
      var finishedAt = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"));
      var startedAt = finishedAt.minusSeconds(1);
      var entitlementFlowEntity = entitlementFlowEntity(APPLICATION_FLOW_ID, FLOW_ID, startedAt, finishedAt);
      var appFlow1 = applicationFlow(APPLICATION_FLOW_ID, FLOW_ID,
        Date.from(startedAt.toInstant()), Date.from(finishedAt.toInstant()));

      when(entitlementFlowRepository.findByFlowId(FLOW_ID)).thenReturn(List.of(entitlementFlowEntity));
      when(entitlementFlowMapper.map(entitlementFlowEntity)).thenReturn(appFlow1);

      var result = entitlementFlowService.findById(FLOW_ID, false);

      assertThat(result).isEqualTo(new EntitlementFlow()
        .id(FLOW_ID)
        .applicationFlows(List.of(appFlow1))
        .startedAt(Date.from(startedAt.toInstant()))
        .finishedAt(Date.from(finishedAt.toInstant()))
        .status(FINISHED)
        .entitlementType(ENTITLE));
    }

    @Test
    void positive_includeStagesIsTrue() {
      var finishedAt = ZonedDateTime.ofInstant(Instant.now(), ZoneId.of("UTC"));
      var startedAt = finishedAt.minusSeconds(1);
      var entitlementFlowEntity = entitlementFlowEntity(APPLICATION_FLOW_ID, FLOW_ID, startedAt, finishedAt);
      var stage = new EntitlementStage().applicationFlowId(APPLICATION_FLOW_ID).status(FINISHED);
      var appFlow1 = applicationFlow(APPLICATION_FLOW_ID, FLOW_ID,
        Date.from(startedAt.toInstant()), Date.from(finishedAt.toInstant()));

      when(entitlementFlowRepository.findByFlowId(FLOW_ID)).thenReturn(List.of(entitlementFlowEntity));
      when(entitlementFlowMapper.map(entitlementFlowEntity)).thenReturn(appFlow1);
      when(entitlementStageService.findStagesForFlow(FLOW_ID)).thenReturn(Map.of(APPLICATION_FLOW_ID, List.of(stage)));

      var result = entitlementFlowService.findById(FLOW_ID, true);

      assertThat(result).isEqualTo(new EntitlementFlow()
        .id(FLOW_ID)
        .applicationFlows(List.of(appFlow1))
        .startedAt(Date.from(startedAt.toInstant()))
        .finishedAt(Date.from(finishedAt.toInstant()))
        .status(FINISHED)
        .entitlementType(ENTITLE));
    }
  }

  @Nested
  @DisplayName("findLastFlows")
  class FindLastFlows {

    @Test
    void positive() {
      var applicationIds = List.of(APPLICATION_ID);
      var entity = entitlementFlowEntity(FLOW_ID);
      var applicationFlow = applicationFlow(FLOW_ID);

      when(entitlementFlowMapper.map(entity)).thenReturn(applicationFlow);
      when(entitlementFlowRepository.findLastEntitlementFlows(applicationIds, TENANT_ID)).thenReturn(List.of(entity));

      var result = entitlementFlowService.findLastFlows(applicationIds, TENANT_ID);

      assertThat(result).isEqualTo(List.of(applicationFlow));
    }

    @Test
    void findLastFlows_positive_emptyListOfApplicationIds() {
      var result = entitlementFlowService.findLastFlows(emptyList(), TENANT_ID);
      assertThat(result).isEmpty();
      verifyNoInteractions(entitlementFlowMapper, entitlementFlowRepository);
    }
  }

  @Nested
  @DisplayName("findLastDependentFlows")
  class FindLastDependentFlows {

    @Test
    void positive() {
      var entity = entitlementFlowEntity(FLOW_ID);
      var applicationFlow = applicationFlow(FLOW_ID);
      var dependency = applicationDependency("test-app", "0.0.1");
      var dependencyEntity = applicationDependencyEntity(dependency);

      when(dependencyService.findAllByParentApplicationName(TENANT_ID, "test-app-0.0.1"))
        .thenReturn(List.of(dependencyEntity));
      when(entitlementFlowRepository.findLastEntitlementFlows(List.of(APPLICATION_ID), TENANT_ID))
        .thenReturn(List.of(entity));
      when(entitlementFlowMapper.map(entity)).thenReturn(applicationFlow);

      var result = entitlementFlowService.findLastDependentFlows("test-app-0.0.1", TENANT_ID);

      assertThat(result).isEqualTo(List.of(applicationFlow));
    }
  }

  @Nested
  @DisplayName("findByApplicationFlowId")
  class FindByApplicationFlowId {

    @Test
    void positive_includeStagesFalse() {
      var entity = entitlementFlowEntity(FLOW_ID);
      var applicationFlow = applicationFlow(FLOW_ID);
      when(entitlementFlowRepository.getReferenceById(APPLICATION_FLOW_ID)).thenReturn(entity);
      when(entitlementFlowMapper.map(entity)).thenReturn(applicationFlow);

      var result = entitlementFlowService.findByApplicationFlowId(APPLICATION_FLOW_ID, false);

      assertThat(result).isEqualTo(applicationFlow);
      verifyNoInteractions(entitlementStageService);
    }

    @Test
    void positive_includeStagesTrue() {
      var entity = entitlementFlowEntity(FLOW_ID);
      var applicationFlow = applicationFlow(FLOW_ID);
      var stages = List.of(new EntitlementStage().name("test").status(FINISHED)
        .applicationFlowId(APPLICATION_FLOW_ID));

      when(entitlementFlowRepository.getReferenceById(APPLICATION_FLOW_ID)).thenReturn(entity);
      when(entitlementFlowMapper.map(entity)).thenReturn(applicationFlow);
      when(entitlementStageService.findEntitlementStages(APPLICATION_FLOW_ID)).thenReturn(SearchResult.of(stages));

      var result = entitlementFlowService.findByApplicationFlowId(APPLICATION_FLOW_ID, true);

      assertThat(result).isEqualTo(applicationFlow);
      assertThat(result.getStages()).isEqualTo(stages);
    }
  }

  @Nested
  @DisplayName("createApplicationFlowsWithStatusQueued")
  class CreateApplicationFlowsWithStatusQueued {

    @Test
    void positive() {
      var flowId = UUID.randomUUID();
      var entity = entitlementFlowEntity(flowId, QUEUED);
      var entities = List.of(entity);
      var expectedApplicationFlows = List.of(applicationFlow(flowId, QUEUED));

      when(entitlementFlowMapper.mapWithStatusQueued(TENANT_ID, APPLICATION_ID, flowId, ENTITLE)).thenReturn(entity);
      when(entitlementFlowRepository.saveAll(entities)).thenReturn(entities);
      when(entitlementFlowMapper.map(entities)).thenReturn(expectedApplicationFlows);

      var entitlementRequest = entitlementRequest(ENTITLE, false);
      var result = entitlementFlowService.createApplicationFlowsWithStatusQueued(flowId, entitlementRequest);

      assertThat(result).isEqualTo(expectedApplicationFlows);
    }
  }

  @Nested
  @DisplayName("createEntitlementFlow")
  class CreateEntitlementFlow {

    @Test
    void positive_entitlementRequest() {
      var request = entitlementRequest(ENTITLE, true);
      var queuedApplicationFlows = List.of(applicationFlow(FLOW_ID, QUEUED));
      var fullApplicationFlowId = fullApplicationFlowId(FLOW_ID, APPLICATION_FLOW_ID);

      when(appManagerService.getApplicationDescriptors(request.getApplications(), request.getOkapiToken()))
        .thenReturn(List.of(applicationDescriptor()));
      when(flowProvider.prepareFlow(fullApplicationFlowId, APPLICATION_ID, IGNORE_ON_ERROR)).thenReturn(flow);

      var actual = entitlementFlowService.createEntitlementFlow(FLOW_ID, request, queuedApplicationFlows);

      var expectedEntitlements = extendedEntitlements(FLOW_ID, extendedEntitlement().flowId(APPLICATION_FLOW_ID));
      assertThat(actual.getEntitlements()).isEqualTo(expectedEntitlements);
    }

    @Test
    void negative_emptyQueuedApplicationFlows() {
      var flowId = UUID.randomUUID();
      var request = entitlementRequest(ENTITLE, false);
      var queuedApplicationFlows = Collections.<ApplicationFlow>emptyList();

      assertThatThrownBy(
        () -> entitlementFlowService.createEntitlementFlow(flowId, request, queuedApplicationFlows))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Application flows cannot be empty");
    }

    @Test
    void positive_entitlementRequestForMultipleApplications() {
      var flowId = UUID.randomUUID();
      var fooApplicationFlowId = UUID.randomUUID();
      var barApplicationFlowId = UUID.randomUUID();
      var fooApplicationId = "test-foo-app-1.0.0";
      var barApplicationId = "test-bar-app-1.0.0";
      var request = entitlementRequest(ENTITLE, fooApplicationId, barApplicationId);
      var queuedApplicationFlows = List.of(
        applicationFlow(fooApplicationFlowId, fooApplicationId, flowId),
        applicationFlow(barApplicationFlowId, barApplicationId, flowId));
      var appDescriptors = List.of(
        applicationDescriptor(fooApplicationId),
        applicationDescriptor(barApplicationId, dependency(fooApplicationId)));

      when(appManagerService.getApplicationDescriptors(request.getApplications(), request.getOkapiToken()))
        .thenReturn(appDescriptors);

      var fooFullApplicationFlowId = fullApplicationFlowId(flowId, fooApplicationFlowId, 0);
      var barFullApplicationFlowId = fullApplicationFlowId(flowId, barApplicationFlowId, 1);
      when(flowProvider.prepareFlow(fooFullApplicationFlowId, fooApplicationId, IGNORE_ON_ERROR)).thenReturn(flow);
      when(flowProvider.prepareFlow(barFullApplicationFlowId, barApplicationId, IGNORE_ON_ERROR)).thenReturn(flow);

      var actual = entitlementFlowService.createEntitlementFlow(flowId, request, queuedApplicationFlows);

      var expectedEntitlements = extendedEntitlements(flowId,
        extendedEntitlement(fooApplicationFlowId, TENANT_ID, fooApplicationId),
        extendedEntitlement(barApplicationFlowId, TENANT_ID, barApplicationId));
      assertThat(actual.getEntitlements()).isEqualTo(expectedEntitlements);
    }

    @Test
    void positive_entitlementRequestWithoutIgnoreErrors() {
      var request = entitlementRequest(ENTITLE, false);
      var queuedApplicationFlows = List.of(applicationFlow(FLOW_ID, QUEUED));
      var fullApplicationFlowId = fullApplicationFlowId(FLOW_ID, APPLICATION_FLOW_ID);

      when(appManagerService.getApplicationDescriptors(request.getApplications(), request.getOkapiToken()))
        .thenReturn(List.of(applicationDescriptor()));
      when(flowProvider.prepareFlow(fullApplicationFlowId, APPLICATION_ID, CANCEL_ON_ERROR)).thenReturn(flow);

      var actual = entitlementFlowService.createEntitlementFlow(FLOW_ID, request, queuedApplicationFlows);

      var expectedEntitlements = extendedEntitlements(FLOW_ID, extendedEntitlement().flowId(APPLICATION_FLOW_ID));
      assertThat(actual.getEntitlements()).isEqualTo(expectedEntitlements);
    }

    @ParameterizedTest
    @CsvSource({"true", "false"})
    @DisplayName("positive_revokeRequest_parameterized")
    void positive_revokeRequest_parameterized(boolean ignoreOnError) {
      var request = entitlementRequest(REVOKE, ignoreOnError);
      var queuedApplicationFlows = List.of(applicationFlow(FLOW_ID, QUEUED));
      var fullApplicationFlowId = fullApplicationFlowId(FLOW_ID, APPLICATION_FLOW_ID);

      when(appManagerService.getApplicationDescriptors(request.getApplications(), request.getOkapiToken()))
        .thenReturn(List.of(applicationDescriptor()));
      when(revokeFlowProvider.prepareFlow(fullApplicationFlowId, APPLICATION_ID)).thenReturn(flow);

      var actual = entitlementFlowService.createEntitlementFlow(FLOW_ID, request, queuedApplicationFlows);

      var expectedEntitlements = extendedEntitlements(FLOW_ID, extendedEntitlement().flowId(APPLICATION_FLOW_ID));
      assertThat(actual.getEntitlements()).isEqualTo(expectedEntitlements);
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

    static EntitlementRequest entitlementRequest(EntitlementType type, boolean ignoreErrors) {
      return EntitlementRequest.builder()
        .type(type)
        .ignoreErrors(ignoreErrors)
        .applications(List.of(APPLICATION_ID))
        .tenantId(TENANT_ID)
        .build();
    }

    static EntitlementFlowEntity entitlementFlowEntity(UUID flowId) {
      return entitlementFlowEntity(APPLICATION_FLOW_ID, flowId, null, null);
    }

    static EntitlementFlowEntity entitlementFlowEntity(UUID flowId, ExecutionStatus status) {
      var entity = entitlementFlowEntity(APPLICATION_FLOW_ID, flowId, null, null);
      entity.setStatus(status);
      return entity;
    }

    static EntitlementFlowEntity entitlementFlowEntity(UUID appFlowId, UUID flowId,
      ZonedDateTime startedAt, ZonedDateTime finishedAt) {
      var entity = new EntitlementFlowEntity();
      entity.setEntitlementFlowId(appFlowId);
      entity.setFlowId(flowId);
      entity.setApplicationId(APPLICATION_ID);
      entity.setTenantId(TENANT_ID);
      entity.setType(ENTITLE);
      entity.setStatus(FINISHED);
      entity.setStartedAt(startedAt);
      entity.setFinishedAt(finishedAt);
      return entity;
    }

    static ApplicationFlow applicationFlow(UUID flowId) {
      return applicationFlow(flowId, FINISHED);
    }

    static ApplicationFlow applicationFlow(UUID flowId, ExecutionStatus status) {
      return applicationFlow(APPLICATION_FLOW_ID, flowId, APPLICATION_ID, status, null, null);
    }

    static ApplicationFlow applicationFlow(UUID appFlowId, String appId, UUID flowId) {
      return applicationFlow(appFlowId, flowId, appId, QUEUED, null, null);
    }

    static ApplicationFlow applicationFlow(UUID appFlowId, UUID flowId, Date startedAt, Date finishedAt) {
      return applicationFlow(appFlowId, flowId, APPLICATION_ID, FINISHED, startedAt, finishedAt);
    }

    static ApplicationFlow applicationFlow(UUID appFlowId, UUID flowId, String applicationId,
      ExecutionStatus status, Date startedAt, Date finishedAt) {
      return new ApplicationFlow()
        .id(appFlowId)
        .flowId(flowId)
        .applicationId(applicationId)
        .tenantId(TENANT_ID)
        .type(ENTITLE)
        .status(status)
        .startedAt(startedAt)
        .finishedAt(finishedAt);
    }

    static String fullApplicationFlowId(UUID flowId, UUID applicationFlowId) {
      return fullApplicationFlowId(flowId, applicationFlowId, 0);
    }

    static String fullApplicationFlowId(UUID flowId, UUID applicationFlowId, int installationLayer) {
      return String.format("%s/appi-l%d/%s", flowId, installationLayer, applicationFlowId);
    }
  }
}
