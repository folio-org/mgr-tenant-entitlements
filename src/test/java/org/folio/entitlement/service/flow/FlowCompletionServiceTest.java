package org.folio.entitlement.service.flow;

import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import org.folio.entitlement.domain.dto.Flow;
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
class FlowCompletionServiceTest {

  @InjectMocks private FlowCompletionService flowCompletionService;

  @Mock private ApplicationFlowService applicationFlowService;
  @Mock private FlowService flowService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void completeIfNoActiveStages_positive() {
    when(applicationFlowService.finishFlowIfNoActiveStages(eq(APPLICATION_FLOW_ID), any(ZonedDateTime.class)))
      .thenReturn(1);
    when(flowService.getTopLevelFlow(APPLICATION_FLOW_ID)).thenReturn(new Flow().id(FLOW_ID));
    when(flowService.finishFlowIfNoActiveStages(eq(FLOW_ID), any(ZonedDateTime.class))).thenReturn(1);

    flowCompletionService.completeIfNoActiveStages(APPLICATION_FLOW_ID, ZonedDateTime.now());
  }

  @Test
  void completeIfNoActiveStages_positive_flowsAlreadyTerminal() {
    when(applicationFlowService.finishFlowIfNoActiveStages(eq(APPLICATION_FLOW_ID), any(ZonedDateTime.class)))
      .thenReturn(0);
    when(flowService.getTopLevelFlow(APPLICATION_FLOW_ID)).thenReturn(new Flow().id(FLOW_ID));
    when(flowService.finishFlowIfNoActiveStages(eq(FLOW_ID), any(ZonedDateTime.class))).thenReturn(0);

    flowCompletionService.completeIfNoActiveStages(APPLICATION_FLOW_ID, ZonedDateTime.now());
  }

  @Test
  void failFlows_positive() {
    when(applicationFlowService.failActiveFlow(eq(APPLICATION_FLOW_ID), any(ZonedDateTime.class))).thenReturn(1);
    when(flowService.getTopLevelFlow(APPLICATION_FLOW_ID)).thenReturn(new Flow().id(FLOW_ID));
    when(flowService.failActiveFlow(eq(FLOW_ID), any(ZonedDateTime.class))).thenReturn(1);

    flowCompletionService.failFlows(APPLICATION_FLOW_ID, ZonedDateTime.now());
  }

  @Test
  void failFlows_positive_flowsAlreadyTerminal() {
    when(applicationFlowService.failActiveFlow(eq(APPLICATION_FLOW_ID), any(ZonedDateTime.class))).thenReturn(0);
    when(flowService.getTopLevelFlow(APPLICATION_FLOW_ID)).thenReturn(new Flow().id(FLOW_ID));
    when(flowService.failActiveFlow(eq(FLOW_ID), any(ZonedDateTime.class))).thenReturn(0);

    flowCompletionService.failFlows(APPLICATION_FLOW_ID, ZonedDateTime.now());
  }
}
