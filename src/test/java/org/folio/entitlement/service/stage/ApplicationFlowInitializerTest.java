package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.NON_TERMINAL_STATUSES;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Map;
import org.folio.entitlement.repository.ApplicationFlowRepository;
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
class ApplicationFlowInitializerTest {

  @InjectMocks private ApplicationFlowInitializer applicationFlowInitializer;

  @Mock private ApplicationFlowRepository applicationFlowRepository;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    when(applicationFlowRepository.updateStatusIfCurrentInAndFlowActive(
      eq(APPLICATION_FLOW_ID), eq(IN_PROGRESS), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class))).thenReturn(1);

    var flowParameters = Map.of(PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, Map.of());

    applicationFlowInitializer.execute(stageContext);

    verify(applicationFlowRepository).updateStatusIfCurrentInAndFlowActive(
      eq(APPLICATION_FLOW_ID), eq(IN_PROGRESS), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class));
  }

  @Test
  void execute_negative_applicationFlowIsAlreadyInTerminalStatus() {
    when(applicationFlowRepository.updateStatusIfCurrentInAndFlowActive(
      eq(APPLICATION_FLOW_ID), eq(IN_PROGRESS), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class))).thenReturn(0);

    var flowParameters = Map.of(PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, Map.of());

    assertThatThrownBy(() -> applicationFlowInitializer.execute(stageContext))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Application flow cannot be started, because it or its flow is already in a terminal status "
        + "[applicationFlowId: %s]", APPLICATION_FLOW_ID);
  }
}
