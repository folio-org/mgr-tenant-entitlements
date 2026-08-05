package org.folio.entitlement.service;

import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.CANCELLED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.model.ResultList.asSinglePage;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.extendedEntitlements;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import org.folio.entitlement.configuration.FlowEngineConfigurationProperties;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.exception.FlowExecutionTimeoutException;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.integration.tm.TenantManagerService;
import org.folio.entitlement.service.flow.FlowProvider;
import org.folio.entitlement.service.flow.FlowService;
import org.folio.entitlement.support.TestUtils;
import org.folio.entitlement.support.TestValues;
import org.folio.flow.api.Flow;
import org.folio.flow.api.FlowEngine;
import org.folio.flow.exception.FlowExecutionException;
import org.folio.flow.model.StageResult;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementServiceTest {

  @InjectMocks private EntitlementService entitlementService;
  @Mock private Flow flow;
  @Mock private FlowEngine flowEngine;
  @Mock private FlowProvider flowProvider;
  @Mock private FlowService flowService;
  @Mock private TenantManagerService tenantManagerService;
  @Mock private EntitlementCrudService crudService;
  @Mock private FlowEngineConfigurationProperties flowEngineProperties;

  @AfterEach
  void tearDown() {
    Thread.interrupted(); // clears the flag set by negative_interrupted, JUnit reuses the thread
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Nested
  @DisplayName("get")
  class Get {

    @Test
    void get_positive() {
      var cqlQuery = "cql.allRecords=1";
      var expectedEntitlement = TestValues.entitlement();
      when(crudService.findByQuery(cqlQuery, false, 0, 100)).thenReturn(asSinglePage(expectedEntitlement));

      var actual = entitlementService.findByQueryOrTenantName(cqlQuery, null, false, 0, 100, OKAPI_TOKEN);

      assertThat(actual).isEqualTo(asSinglePage(expectedEntitlement));
    }

    @Test
    void get_positive_byTenant() {
      var cqlQuery = "tenantId==" + TENANT_ID;
      var expectedEntitlement = TestValues.entitlement();

      when(tenantManagerService.findTenantByName(TENANT_NAME, OKAPI_TOKEN)).thenReturn(TestValues.tenant());
      when(crudService.findByQuery(cqlQuery, false, 0, 100)).thenReturn(asSinglePage(expectedEntitlement));

      var actual = entitlementService.findByQueryOrTenantName(null, TENANT_NAME, false, 0, 100, OKAPI_TOKEN);

      assertThat(actual).isEqualTo(asSinglePage(expectedEntitlement));
    }

    @Test
    void get_negative_queryAndTenantSpecified() {
      assertThatThrownBy(() -> entitlementService.findByQueryOrTenantName(
          "cql.allRecords=1", TENANT_NAME, false, 0, 100, OKAPI_TOKEN))
        .isInstanceOf(RequestValidationException.class)
        .hasMessage("Both 'query' and 'tenant' parameters are provided but only one of them has to be specified");
    }
  }

  @Nested
  @DisplayName("performRequest")
  class PerformRequest {

    @Test
    void positive_validatorIsCalled() {
      var request = entitlementRequest(false);
      var extendedEntitlements = extendedEntitlements(FLOW_ID, entitlement());
      when(flowProvider.createFlow(request)).thenReturn(flow);
      when(flow.getId()).thenReturn(FLOW_ID.toString());

      var actual = entitlementService.performRequest(request);

      assertThat(actual).isEqualTo(extendedEntitlements);
      verify(flowEngine).execute(flow);
    }

    @Test
    void positive_async() {
      var request = entitlementRequest(true);
      var extendedEntitlements = extendedEntitlements(FLOW_ID, entitlement());

      when(flowProvider.createFlow(request)).thenReturn(flow);
      when(flow.getId()).thenReturn(FLOW_ID.toString());
      when(flowEngine.executeAsync(flow)).thenReturn(completedFuture(null));

      var actual = entitlementService.performRequest(request);

      assertThat(actual).isEqualTo(extendedEntitlements);
      verify(flowEngine).executeAsync(flow);
    }

    @Test
    void negative_executionTimeout() {
      var request = entitlementRequest(false);
      var cause = new TimeoutException("Flow execution timed out");
      var timeout = Duration.ofMinutes(30);

      when(flowProvider.createFlow(request)).thenReturn(flow);
      when(flow.getId()).thenReturn(FLOW_ID.toString());
      when(flowEngineProperties.getExecutionTimeout()).thenReturn(timeout);
      when(flowService.failIfNotTerminal(FLOW_ID)).thenReturn(true);
      doThrow(new FlowExecutionException("Failed to execute flow", FLOW_ID.toString(), cause))
        .when(flowEngine).execute(flow);

      assertThatThrownBy(() -> entitlementService.performRequest(request))
        .isInstanceOf(FlowExecutionTimeoutException.class)
        .hasMessage("Flow '%s' finished with status: FAILED", FLOW_ID)
        .hasCause(cause)
        .extracting("flowId", "timeout").containsExactly(FLOW_ID, timeout);
    }

    @Test
    void negative_flowExecutionErrorIsRethrown() {
      var request = entitlementRequest(false);
      var exception = new FlowExecutionException("Failed to execute flow", FLOW_ID.toString(),
        new IllegalStateException("stage failed"));

      when(flowProvider.createFlow(request)).thenReturn(flow);
      doThrow(exception).when(flowEngine).execute(flow);

      assertThatThrownBy(() -> entitlementService.performRequest(request)).isSameAs(exception);

      verifyNoInteractions(flowService, flowEngineProperties);
    }

    @Test
    void negative_interrupted() {
      var request = entitlementRequest(false);
      var timeout = Duration.ofMinutes(30);

      when(flowProvider.createFlow(request)).thenReturn(flow);
      when(flow.getId()).thenReturn(FLOW_ID.toString());
      when(flowEngineProperties.getExecutionTimeout()).thenReturn(timeout);
      when(flowService.failIfNotTerminal(FLOW_ID)).thenReturn(true);
      doAnswer(invocation -> {
        Thread.currentThread().interrupt();
        return null;
      }).when(flowEngine).execute(flow);

      assertThatThrownBy(() -> entitlementService.performRequest(request))
        .isInstanceOf(FlowExecutionTimeoutException.class)
        .hasCauseInstanceOf(InterruptedException.class);

      assertThat(Thread.currentThread().isInterrupted()).isFalse();
    }

    @Test
    void negative_stageTimeoutExceptionIsRethrown() {
      var request = entitlementRequest(false);
      var cause = new TimeoutException("Stage execution timed out");
      var stageResult = StageResult.builder()
        .flowId(FLOW_ID.toString())
        .stageId("moduleInstaller")
        .status(org.folio.flow.model.ExecutionStatus.FAILED)
        .build();
      var exception = new FlowExecutionException(FLOW_ID.toString(), "moduleInstaller", List.of(stageResult), cause);

      when(flowProvider.createFlow(request)).thenReturn(flow);
      doThrow(exception).when(flowEngine).execute(flow);

      assertThatThrownBy(() -> entitlementService.performRequest(request)).isSameAs(exception);

      verifyNoInteractions(flowService, flowEngineProperties);
    }

    @Test
    void positive_timeoutButFlowFinishedConcurrently() {
      var request = entitlementRequest(false);
      var cause = new TimeoutException("Flow execution timed out");
      var expectedEntitlements = extendedEntitlements(FLOW_ID, entitlement());

      when(flowProvider.createFlow(request)).thenReturn(flow);
      when(flow.getId()).thenReturn(FLOW_ID.toString());
      doThrow(new FlowExecutionException("Failed to execute flow", FLOW_ID.toString(), cause))
        .when(flowEngine).execute(flow);
      when(flowService.failIfNotTerminal(FLOW_ID)).thenReturn(false);
      when(flowService.findStatus(FLOW_ID)).thenReturn(Optional.of(FINISHED));

      var actual = entitlementService.performRequest(request);

      assertThat(actual).isEqualTo(expectedEntitlements);
    }

    @Test
    void negative_timeoutFlowCancelledConcurrently() {
      var request = entitlementRequest(false);
      var cause = new TimeoutException("Flow execution timed out");
      var timeout = Duration.ofMinutes(30);

      when(flowProvider.createFlow(request)).thenReturn(flow);
      when(flow.getId()).thenReturn(FLOW_ID.toString());
      when(flowEngineProperties.getExecutionTimeout()).thenReturn(timeout);
      doThrow(new FlowExecutionException("Failed to execute flow", FLOW_ID.toString(), cause))
        .when(flowEngine).execute(flow);
      when(flowService.failIfNotTerminal(FLOW_ID)).thenReturn(false);
      when(flowService.findStatus(FLOW_ID)).thenReturn(Optional.of(CANCELLED));

      assertThatThrownBy(() -> entitlementService.performRequest(request))
        .isInstanceOf(FlowExecutionTimeoutException.class)
        .hasMessage("Flow '%s' finished with status: CANCELLED", FLOW_ID)
        .hasCause(cause)
        .extracting("flowId", "timeout").containsExactly(FLOW_ID, timeout);
    }

    @Test
    void negative_timeoutFlowCreatedConcurrently() {
      var request = entitlementRequest(false);
      var cause = new TimeoutException("Flow execution timed out");
      var timeout = Duration.ofMinutes(30);

      when(flowProvider.createFlow(request)).thenReturn(flow);
      when(flow.getId()).thenReturn(FLOW_ID.toString());
      when(flowEngineProperties.getExecutionTimeout()).thenReturn(timeout);
      doThrow(new FlowExecutionException("Failed to execute flow", FLOW_ID.toString(), cause))
        .when(flowEngine).execute(flow);
      when(flowService.failIfNotTerminal(FLOW_ID)).thenReturn(false, true);
      when(flowService.findStatus(FLOW_ID)).thenReturn(Optional.of(IN_PROGRESS));

      assertThatThrownBy(() -> entitlementService.performRequest(request))
        .isInstanceOf(FlowExecutionTimeoutException.class)
        .hasMessage("Flow '%s' finished with status: FAILED", FLOW_ID)
        .hasCause(cause)
        .extracting("flowId", "timeout").containsExactly(FLOW_ID, timeout);

      verify(flowService, times(2)).failIfNotTerminal(FLOW_ID);
    }

    @Test
    void negative_timeoutBeforeFlowStarted() {
      var request = entitlementRequest(false);
      var cause = new TimeoutException("Flow execution timed out");
      var timeout = Duration.ofMinutes(30);

      when(flowProvider.createFlow(request)).thenReturn(flow);
      when(flow.getId()).thenReturn(FLOW_ID.toString());
      when(flowEngineProperties.getExecutionTimeout()).thenReturn(timeout);
      doThrow(new FlowExecutionException("Failed to execute flow", FLOW_ID.toString(), cause))
        .when(flowEngine).execute(flow);
      when(flowService.failIfNotTerminal(FLOW_ID)).thenReturn(false);
      when(flowService.findStatus(FLOW_ID)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> entitlementService.performRequest(request))
        .isInstanceOf(FlowExecutionTimeoutException.class)
        .hasMessage("Flow '%s' finished with status: FAILED", FLOW_ID)
        .hasCause(cause)
        .extracting("flowId", "timeout").containsExactly(FLOW_ID, timeout);

      verify(flowService).createFailed(FLOW_ID, request);
    }

    @Test
    void negative_timeoutBeforeFlowStarted_concurrentCreate() {
      var request = entitlementRequest(false);
      var cause = new TimeoutException("Flow execution timed out");
      var timeout = Duration.ofMinutes(30);

      when(flowProvider.createFlow(request)).thenReturn(flow);
      when(flow.getId()).thenReturn(FLOW_ID.toString());
      when(flowEngineProperties.getExecutionTimeout()).thenReturn(timeout);
      doThrow(new FlowExecutionException("Failed to execute flow", FLOW_ID.toString(), cause))
        .when(flowEngine).execute(flow);
      when(flowService.failIfNotTerminal(FLOW_ID)).thenReturn(false);
      when(flowService.findStatus(FLOW_ID)).thenReturn(Optional.empty());
      doThrow(new DataIntegrityViolationException("duplicate key"))
        .when(flowService).createFailed(FLOW_ID, request);

      assertThatThrownBy(() -> entitlementService.performRequest(request))
        .isInstanceOf(FlowExecutionTimeoutException.class)
        .hasMessage("Flow '%s' finished with status: FAILED", FLOW_ID)
        .hasCause(cause);

      verify(flowService, times(2)).failIfNotTerminal(FLOW_ID);
    }

    private static EntitlementRequest entitlementRequest(boolean async) {
      return EntitlementRequest.builder()
        .applications(List.of(APPLICATION_ID))
        .tenantId(TENANT_ID)
        .async(async)
        .type(ENTITLE)
        .build();
    }
  }
}
