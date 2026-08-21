package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.service.FlowStageService;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.folio.entitlement.service.flow.FlowService;
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

  @InjectMocks private ResourceResultEventService eventService;

  @Mock private FlowStageService stageService;
  @Mock private ApplicationFlowService applicationFlowService;
  @Mock private FlowService flowService;

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
  void processEvent_positive_successResult_stageAndFlowsFinished() {
    var stage = new FlowStage().id(STAGE_ID).flowId(APPLICATION_FLOW_ID).status(IN_PROGRESS);
    when(stageService.findById(STAGE_ID)).thenReturn(Optional.of(stage));
    when(flowService.getTopLevelFlow(APPLICATION_FLOW_ID)).thenReturn(new Flow().id(FLOW_ID));

    eventService.processEvent(resourceResultEvent(ResourceResultStatus.SUCCESS));

    verify(stageService).finishActiveStage(eq(STAGE_ID), any(ZonedDateTime.class));
    verify(applicationFlowService).finishFlowIfNoActiveStages(eq(APPLICATION_FLOW_ID), any(ZonedDateTime.class));
    verify(flowService).getTopLevelFlow(APPLICATION_FLOW_ID);
    verify(flowService).finishFlowIfNoActiveStages(eq(FLOW_ID), any(ZonedDateTime.class));
  }

  @Test
  void processEvent_positive_failureResult_stageAndFlowsFailed() {
    var details = "Module registration failed";
    var stage = new FlowStage().id(STAGE_ID).flowId(APPLICATION_FLOW_ID).status(IN_PROGRESS);
    when(stageService.findById(STAGE_ID)).thenReturn(Optional.of(stage));
    when(flowService.getTopLevelFlow(APPLICATION_FLOW_ID)).thenReturn(new Flow().id(FLOW_ID));

    eventService.processEvent(resourceResultEvent(ResourceResultStatus.FAILURE, details));

    verify(stageService).failActiveStage(eq(STAGE_ID), eq(details), any(ZonedDateTime.class));
    verify(applicationFlowService).failActiveFlow(eq(APPLICATION_FLOW_ID), any(ZonedDateTime.class));
    verify(flowService).getTopLevelFlow(APPLICATION_FLOW_ID);
    verify(flowService).failActiveFlow(eq(FLOW_ID), any(ZonedDateTime.class));
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
