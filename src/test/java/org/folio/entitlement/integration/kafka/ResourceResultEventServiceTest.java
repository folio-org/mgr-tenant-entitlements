package org.folio.entitlement.integration.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.integration.kafka.ResourceResultEventService.ASYNC_FAILURE_ERROR_TYPE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.service.FlowStageService;
import org.folio.entitlement.service.flow.FlowCompletionService;
import org.folio.entitlement.support.TestUtils;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.integration.kafka.model.ResourceResultStatus;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ResourceResultEventServiceTest {

  private static final UUID STAGE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final String NO_DETAILS_MESSAGE = "Downstream service reported a failure with no details";

  @InjectMocks private ResourceResultEventService eventService;

  @Mock private FlowStageService stageService;
  @Mock private FlowCompletionService flowCompletionService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void processEvent_positive_stageNotFound_ignored() {
    when(stageService.findById(STAGE_ID)).thenReturn(Optional.empty());

    eventService.processEvent(resourceResultEvent(ResourceResultStatus.SUCCESS));
  }

  @Test
  void processEvent_positive_stageNotInProgress_ignored() {
    var stage = new FlowStage().id(STAGE_ID).flowId(APPLICATION_FLOW_ID).status(FINISHED);
    when(stageService.findById(STAGE_ID)).thenReturn(Optional.of(stage));

    eventService.processEvent(resourceResultEvent(ResourceResultStatus.SUCCESS));
  }

  @Test
  void processEvent_negative_nonUuidId_throwsIllegalArgument() {
    var event = ResourceResultEvent.builder()
      .id("not-a-uuid")
      .tenant(TENANT_NAME)
      .status(ResourceResultStatus.SUCCESS)
      .build();

    assertThatThrownBy(() -> eventService.processEvent(event))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void processEvent_positive_successResult_stageAndFlowsFinished() {
    var stage = new FlowStage().id(STAGE_ID).flowId(APPLICATION_FLOW_ID).status(IN_PROGRESS);
    when(stageService.findById(STAGE_ID)).thenReturn(Optional.of(stage));
    when(stageService.finishActiveStage(eq(STAGE_ID), any(ZonedDateTime.class))).thenReturn(1);

    eventService.processEvent(resourceResultEvent(ResourceResultStatus.SUCCESS));

    verify(flowCompletionService).completeIfNoActiveStages(eq(APPLICATION_FLOW_ID), any(ZonedDateTime.class));
  }

  @Test
  void processEvent_positive_successResult_casLost_ignored() {
    var stage = new FlowStage().id(STAGE_ID).flowId(APPLICATION_FLOW_ID).status(IN_PROGRESS);
    when(stageService.findById(STAGE_ID)).thenReturn(Optional.of(stage));
    when(stageService.finishActiveStage(eq(STAGE_ID), any(ZonedDateTime.class))).thenReturn(0);

    eventService.processEvent(resourceResultEvent(ResourceResultStatus.SUCCESS));
  }

  @Test
  void processEvent_positive_failureResult_stageAndFlowsFailed() {
    var details = "Module registration failed";
    var stage = new FlowStage().id(STAGE_ID).flowId(APPLICATION_FLOW_ID).status(IN_PROGRESS);
    when(stageService.findById(STAGE_ID)).thenReturn(Optional.of(stage));
    when(stageService.failActiveStage(
      eq(STAGE_ID), eq(ASYNC_FAILURE_ERROR_TYPE), eq(details), any(ZonedDateTime.class))).thenReturn(1);

    eventService.processEvent(resourceResultEvent(ResourceResultStatus.FAILURE, details));

    verify(flowCompletionService).failFlows(eq(APPLICATION_FLOW_ID), any(ZonedDateTime.class));
  }

  @Test
  void processEvent_positive_failureResult_casLost_ignored() {
    var stage = new FlowStage().id(STAGE_ID).flowId(APPLICATION_FLOW_ID).status(IN_PROGRESS);
    when(stageService.findById(STAGE_ID)).thenReturn(Optional.of(stage));
    when(stageService.failActiveStage(
      eq(STAGE_ID), eq(ASYNC_FAILURE_ERROR_TYPE), any(String.class), any(ZonedDateTime.class))).thenReturn(0);

    eventService.processEvent(resourceResultEvent(ResourceResultStatus.FAILURE, "some-error"));
  }

  @Test
  void processEvent_positive_failureResult_nullDetails_usesDefaultMessage() {
    var stage = new FlowStage().id(STAGE_ID).flowId(APPLICATION_FLOW_ID).status(IN_PROGRESS);
    when(stageService.findById(STAGE_ID)).thenReturn(Optional.of(stage));
    when(stageService.failActiveStage(
      eq(STAGE_ID), eq(ASYNC_FAILURE_ERROR_TYPE), eq(NO_DETAILS_MESSAGE), any(ZonedDateTime.class))).thenReturn(1);

    eventService.processEvent(resourceResultEvent(ResourceResultStatus.FAILURE));

    verify(flowCompletionService).failFlows(eq(APPLICATION_FLOW_ID), any(ZonedDateTime.class));
  }

  private static ResourceResultEvent resourceResultEvent(ResourceResultStatus status) {
    return resourceResultEvent(status, null);
  }

  private static ResourceResultEvent resourceResultEvent(ResourceResultStatus status, String details) {
    return ResourceResultEvent.builder()
      .id(STAGE_ID.toString())
      .tenant(TENANT_NAME)
      .status(status)
      .details(details)
      .build();
  }
}
