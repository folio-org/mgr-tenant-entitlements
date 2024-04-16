package org.folio.entitlement.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.CANCELLED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FAILED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.dto.ExecutionStatus.QUEUED;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.utils.EntitlementServiceUtils.prepareFlowResponse;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@UnitTest
class EntitlementServiceUtilsTest {

  @Test
  void prepareFlowResponse_positive() {
    var startedAt1 = Instant.now().minusSeconds(45);
    var startedAt2 = Instant.now().minusSeconds(60);
    var startedAt3 = Instant.now().minusSeconds(20);

    var finishedAt1 = Instant.now().minusSeconds(10);
    var finishedAt2 = Instant.now().minusSeconds(5);
    var finishedAt3 = Instant.now().minusSeconds(8);

    var flow1 = applicationFlow(UUID.randomUUID(), startedAt1, finishedAt1);
    var flow2 = applicationFlow(UUID.randomUUID(), startedAt2, finishedAt2);
    var flow3 = applicationFlow(UUID.randomUUID(), startedAt3, finishedAt3);

    var applicationFlows = List.of(flow1, flow2, flow3);

    var result = prepareFlowResponse(applicationFlows, Collections.emptyMap());

    var expectedFlow = new Flow()
      .id(FLOW_ID)
      .type(ENTITLE)
      .status(FINISHED)
      .startedAt(Date.from(startedAt2))
      .finishedAt(Date.from(finishedAt2))
      .applicationFlows(applicationFlows);
    assertThat(result).isEqualTo(expectedFlow);
  }

  @ParameterizedTest
  @MethodSource("finalFlowStatusDataProvider")
  @DisplayName("getFinalFlowStatus_parameterized")
  void getFinalFlowStatus_parameterized(Set<ExecutionStatus> flowStatuses, ExecutionStatus expectedValue) {
    var result = EntitlementServiceUtils.getFinalFlowStatus(flowStatuses);
    assertThat(result).isEqualTo(Optional.ofNullable(expectedValue));
  }

  private static Stream<Arguments> finalFlowStatusDataProvider() {
    return Stream.of(
      arguments(Set.of(), null),
      arguments(Set.of(QUEUED, IN_PROGRESS, CANCELLED, FAILED, FINISHED), QUEUED),
      arguments(Set.of(FINISHED, FAILED, CANCELLED, IN_PROGRESS, QUEUED), QUEUED),
      arguments(Set.of(IN_PROGRESS, CANCELLED, FAILED, FINISHED), IN_PROGRESS),
      arguments(Set.of(FINISHED, IN_PROGRESS, CANCELLED, FAILED), IN_PROGRESS),
      arguments(Set.of(CANCELLED, FAILED, FINISHED), CANCELLED),
      arguments(Set.of(FAILED, FINISHED), FAILED),
      arguments(Set.of(FINISHED), FINISHED)
    );
  }

  private static ApplicationFlow applicationFlow(UUID appFlowId, Instant startedAt, Instant finishedAt) {
    return new ApplicationFlow()
      .id(appFlowId)
      .flowId(FLOW_ID)
      .applicationId(APPLICATION_ID)
      .tenantId(TENANT_ID)
      .type(ENTITLE)
      .status(FINISHED)
      .startedAt(Date.from(startedAt))
      .finishedAt(Date.from(finishedAt));
  }
}
