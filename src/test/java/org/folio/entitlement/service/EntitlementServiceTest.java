package org.folio.entitlement.service;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static java.util.concurrent.CompletableFuture.failedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.dto.ExecutionStatus.QUEUED;
import static org.folio.entitlement.domain.model.ResultList.asSinglePage;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.extendedEntitlement;
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import jakarta.persistence.EntityNotFoundException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementFlow;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementFlowHolder;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.flow.EntitlementFlowService;
import org.folio.entitlement.support.MockTransactionOperations;
import org.folio.entitlement.support.TestValues;
import org.folio.flow.api.Flow;
import org.folio.flow.api.FlowEngine;
import org.folio.flow.exception.StageExecutionException;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionOperations;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

  @InjectMocks
  private EntitlementService entitlementService;

  @Mock private Flow flow;
  @Mock private FlowEngine flowEngine;
  @Mock private EntitlementCrudService crudService;
  @Mock private EntitlementFlowService flowService;
  @Mock private EntitlementValidationService validationService;
  @Spy private TransactionOperations trxOperations = new MockTransactionOperations();

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(flowEngine, crudService, flowService, validationService);
  }

  @Nested
  @DisplayName("get")
  class Get {

    @Test
    void get_positive() {
      var cqlQuery = "cql.allRecords=1";
      var expectedEntitlement = TestValues.entitlement();
      when(crudService.findByQuery(cqlQuery, false, 0, 100)).thenReturn(asSinglePage(expectedEntitlement));

      var actual = entitlementService.findByQuery(cqlQuery, false, 0, 100);

      assertThat(actual).isEqualTo(asSinglePage(expectedEntitlement));
    }
  }

  @Nested
  @DisplayName("execute")
  class Execute {

    @Test
    void positive_validatorIsCalled() {
      var request = entitlementRequest(ENTITLE, false);
      doNothing().when(validationService).validate(request);

      var appFlows = List.of(applicationFlow());
      var extendedEntitlements = extendedEntitlements(extendedEntitlement());
      var flowHandler = EntitlementFlowHolder.of(flow, extendedEntitlements);
      when(flowService.createApplicationFlowsWithStatusQueued(notNull(), eq(request))).thenReturn(appFlows);
      when(flowService.createEntitlementFlow(notNull(), eq(request), eq(appFlows))).thenReturn(flowHandler);

      var actual = entitlementService.execute(request);

      assertThat(actual).isEqualTo(extendedEntitlements);
      verify(flowEngine).execute(flow);
      verify(validationService).validate(request);
    }

    @Test
    void positive_async() {
      var request = entitlementRequest(ENTITLE, true);
      doNothing().when(validationService).validate(request);

      var appFlows = List.of(applicationFlow());
      var extendedEntitlements = extendedEntitlements(extendedEntitlement());
      var flowHandler = EntitlementFlowHolder.of(flow, extendedEntitlements);
      when(flowService.createApplicationFlowsWithStatusQueued(notNull(), eq(request))).thenReturn(appFlows);
      when(flowService.createEntitlementFlow(notNull(), eq(request), eq(appFlows))).thenReturn(
        flowHandler);
      when(flowEngine.executeAsync(flow)).thenReturn(completedFuture(null));

      var actual = entitlementService.execute(request);

      assertThat(actual).isEqualTo(extendedEntitlements);
      verify(flowEngine).executeAsync(flow);
      verify(validationService).validate(request);
    }

    @Test
    void negative_validationError() {
      var entitlementRequest = entitlementRequest(REVOKE, false);
      var errorMessage = "Entitlements are not found for applications: [test-app-1.0.0]";

      doThrow(new EntityNotFoundException(errorMessage)).when(validationService).validate(entitlementRequest);

      assertThatThrownBy(() -> entitlementService.execute(entitlementRequest))
        .isInstanceOf(EntityNotFoundException.class)
        .hasMessage(errorMessage);
      verifyNoInteractions(flowService);
    }

    @Test
    void negative_stageExecutionException_queuedAppFlowsDeleted() {
      var request = entitlementRequest(ENTITLE, false);
      doNothing().when(validationService).validate(request);

      var flowId = UUID.randomUUID().toString();
      when(flow.getId()).thenReturn(flowId);

      var appFlow = applicationFlow();
      var appFlows = List.of(appFlow);
      var extendedEntitlements = extendedEntitlements(extendedEntitlement());
      var flowHandler = EntitlementFlowHolder.of(flow, extendedEntitlements);
      when(flowService.createApplicationFlowsWithStatusQueued(notNull(), eq(request))).thenReturn(appFlows);
      when(flowService.createEntitlementFlow(notNull(), eq(request), eq(appFlows))).thenReturn(flowHandler);

      var entitlementFlow = entitlementFlow(UUID.fromString(flowId), appFlows);
      when(flowService.findById(UUID.fromString(flowId), false)).thenReturn(entitlementFlow);
      doNothing().when(flowService).deleteApplicationFlow(appFlow.getId());

      var msg = "Stage exception";
      doThrow(new StageExecutionException(msg, flowId, Collections.emptyList(), null)).when(flowEngine).execute(flow);

      assertThatThrownBy(() -> entitlementService.execute(request))
        .isInstanceOf(StageExecutionException.class)
        .hasMessage(msg);
    }

    @Test
    void negative_async_stageExecutionException_queuedAppFlowsDeleted() {
      var request = entitlementRequest(ENTITLE, true);
      doNothing().when(validationService).validate(request);

      var flowId = UUID.randomUUID().toString();
      when(flow.getId()).thenReturn(flowId);

      var appFlow = applicationFlow();
      var appFlows = List.of(appFlow);
      var extendedEntitlements = extendedEntitlements(extendedEntitlement());
      var flowHandler = EntitlementFlowHolder.of(flow, extendedEntitlements);
      when(flowService.createApplicationFlowsWithStatusQueued(notNull(), eq(request))).thenReturn(appFlows);
      when(flowService.createEntitlementFlow(notNull(), eq(request), eq(appFlows))).thenReturn(flowHandler);

      var entitlementFlow = entitlementFlow(UUID.fromString(flowId), appFlows);
      when(flowService.findById(UUID.fromString(flowId), false)).thenReturn(entitlementFlow);
      doNothing().when(flowService).deleteApplicationFlow(appFlow.getId());

      var exc = new StageExecutionException("Stage exception", flowId, Collections.emptyList(), null);
      when(flowEngine.executeAsync(flow)).thenReturn(failedFuture(exc));

      var actual = entitlementService.execute(request);

      assertThat(actual).isEqualTo(extendedEntitlements);
    }

    private static EntitlementRequest entitlementRequest(EntitlementType type, boolean async) {
      return EntitlementRequest.builder()
        .applications(List.of(APPLICATION_ID))
        .tenantId(TENANT_ID)
        .async(async)
        .type(type)
        .build();
    }

    private static ApplicationFlow applicationFlow() {
      return new ApplicationFlow()
        .id(UUID.randomUUID())
        .applicationId(APPLICATION_ID)
        .tenantId(TENANT_ID)
        .type(ENTITLE)
        .status(QUEUED);
    }

    private static EntitlementFlow entitlementFlow(UUID flowId, List<ApplicationFlow> applicationFlows) {
      return new EntitlementFlow()
        .id(flowId)
        .entitlementType(ENTITLE)
        .status(IN_PROGRESS)
        .applicationFlows(applicationFlows);
    }
  }
}
