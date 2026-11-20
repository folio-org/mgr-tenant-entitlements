package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.service.stage.EventConfirmationWaitStage.TIMEOUT_ERROR_TYPE;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.folio.entitlement.configuration.EventConfirmationWaitProperties;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.integration.kafka.AbstractModuleEventPublisher;
import org.folio.entitlement.integration.kafka.CapabilitiesModuleEventPublisher;
import org.folio.entitlement.integration.kafka.SystemUserModuleEventPublisher;
import org.folio.entitlement.service.FlowStageService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EventConfirmationWaitStageTest {

  private static final String MOD_A = "mod-a-1.0.0";
  private static final String MOD_B = "mod-b-1.0.0";
  private static final String CAPABILITY_STAGE_SUFFIX = "-capabilitiesModuleEventPublisher";
  private static final String SCHEDULED_JOB_STAGE_SUFFIX = "-scheduledJobModuleEventPublisher";
  private static final String SYSTEM_USER_STAGE_SUFFIX = "-systemUserModuleEventPublisher";
  private static final UUID EVENT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
  private static final UUID EVENT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

  @Mock private FlowStageService flowStageService;
  @Mock private ApplicationStageContext context;

  private EventConfirmationWaitProperties properties;
  private EventConfirmationWaitStage stage;

  @BeforeEach
  void setUp() {
    properties = new EventConfirmationWaitProperties();
    properties.setEnabled(true);
    properties.setPollInterval(Duration.ofMillis(10));
    properties.setMaxWait(Duration.ofMillis(200));

    stage = new EventConfirmationWaitStage("EventConfirmationWait-phase1",
      List.of(SystemUserModuleEventPublisher.class, CapabilitiesModuleEventPublisher.class), properties,
      flowStageService);
  }

  @Test
  void constructor_negative_emptyPublishersAreRejected() {
    var publishers = List.<Class<? extends AbstractModuleEventPublisher<?>>>of();

    assertThatThrownBy(() -> new EventConfirmationWaitStage("EventConfirmationWait-phase1", publishers, properties,
      flowStageService))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("At least one publisher is required: EventConfirmationWait-phase1");
  }

  @Test
  void defaults_positive_pollEvery2SecondsAndWaitUpTo5Minutes() {
    var defaults = new EventConfirmationWaitProperties();

    assertThat(defaults.isEnabled()).isFalse();
    assertThat(defaults.getPollInterval()).isEqualTo(Duration.ofSeconds(2));
    assertThat(defaults.getMaxWait()).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  void execute_positive_disabledIsNoOp() {
    properties.setEnabled(false);

    stage.execute(context);

    verify(flowStageService, never()).findByFlowIdAndStatuses(any(), any());
  }

  @Test
  void execute_positive_allEventsConfirmed() {
    when(context.getCurrentFlowId()).thenReturn(APPLICATION_FLOW_ID);
    when(flowStageService.findByFlowIdAndStatuses(eq(APPLICATION_FLOW_ID), any())).thenReturn(List.of());

    assertThatCode(() -> stage.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_confirmationArrivesWhileWaiting() {
    when(context.getCurrentFlowId()).thenReturn(APPLICATION_FLOW_ID);
    when(flowStageService.findByFlowIdAndStatuses(eq(APPLICATION_FLOW_ID), any()))
      .thenReturn(List.of(stage(EVENT_A, MOD_A + CAPABILITY_STAGE_SUFFIX, ExecutionStatus.IN_PROGRESS)))
      .thenReturn(List.of(stage(EVENT_A, MOD_A + CAPABILITY_STAGE_SUFFIX, ExecutionStatus.IN_PROGRESS)))
      .thenReturn(List.of());

    assertThatCode(() -> stage.execute(context)).doesNotThrowAnyException();
  }

  @Test
  void execute_positive_ignoresStagesOutsideThePhase() {
    when(context.getCurrentFlowId()).thenReturn(APPLICATION_FLOW_ID);
    when(flowStageService.findByFlowIdAndStatuses(eq(APPLICATION_FLOW_ID), any())).thenReturn(List.of(
      stage(EVENT_A, MOD_A + "-folioModuleEventPublisher", ExecutionStatus.IN_PROGRESS),
      stage(EVENT_B, MOD_B + SCHEDULED_JOB_STAGE_SUFFIX,
        ExecutionStatus.IN_PROGRESS)));

    assertThatCode(() -> stage.execute(context)).doesNotThrowAnyException();
    verify(flowStageService, never()).failActiveStage(any(), anyString(), anyString(), any());
  }

  @Test
  void execute_negative_timeoutNamesUnconfirmedEventsAndFailsThem() {
    when(context.getCurrentFlowId()).thenReturn(APPLICATION_FLOW_ID);
    when(flowStageService.findByFlowIdAndStatuses(eq(APPLICATION_FLOW_ID), any())).thenReturn(List.of(
      stage(EVENT_A, MOD_A + CAPABILITY_STAGE_SUFFIX, ExecutionStatus.IN_PROGRESS),
      stage(EVENT_B, MOD_B + SYSTEM_USER_STAGE_SUFFIX, ExecutionStatus.IN_PROGRESS)));

    assertThatThrownBy(() -> stage.execute(context))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining(MOD_A + CAPABILITY_STAGE_SUFFIX + " (eventId=" + EVENT_A + ")")
      .hasMessageContaining(MOD_B + SYSTEM_USER_STAGE_SUFFIX + " (eventId=" + EVENT_B + ")");

    verify(flowStageService).failActiveStage(eq(EVENT_A), eq(TIMEOUT_ERROR_TYPE), contains(MOD_A), any());
    verify(flowStageService).failActiveStage(eq(EVENT_B), eq(TIMEOUT_ERROR_TYPE), contains(MOD_B), any());
  }

  @Test
  void execute_negative_partiallyConfirmedNamesOnlyTheMissingEvent() {
    when(context.getCurrentFlowId()).thenReturn(APPLICATION_FLOW_ID);
    when(flowStageService.findByFlowIdAndStatuses(eq(APPLICATION_FLOW_ID), any())).thenReturn(List.of(
      stage(EVENT_B, MOD_B + CAPABILITY_STAGE_SUFFIX, ExecutionStatus.IN_PROGRESS)));

    assertThatThrownBy(() -> stage.execute(context))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining(EVENT_B.toString())
      .message().doesNotContain(EVENT_A.toString());

    verify(flowStageService).failActiveStage(eq(EVENT_B), eq(TIMEOUT_ERROR_TYPE), anyString(), any());
  }

  @Test
  void execute_negative_failureConfirmationFailsImmediatelyWithoutWaiting() {
    properties.setMaxWait(Duration.ofMinutes(5));
    when(context.getCurrentFlowId()).thenReturn(APPLICATION_FLOW_ID);
    var failed = stage(EVENT_A, MOD_A + CAPABILITY_STAGE_SUFFIX, ExecutionStatus.FAILED);
    failed.setErrorMessage("capability processing failed");
    when(flowStageService.findByFlowIdAndStatuses(eq(APPLICATION_FLOW_ID), any())).thenReturn(List.of(
      failed, stage(EVENT_B, MOD_B + CAPABILITY_STAGE_SUFFIX, ExecutionStatus.IN_PROGRESS)));

    assertThatThrownBy(() -> stage.execute(context))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("capability processing failed")
      .hasMessageContaining(EVENT_A.toString());

    verify(flowStageService, never()).failActiveStage(any(), anyString(), anyString(), any());
  }

  @Test
  void execute_positive_queriesOnlyInProgressAndFailedStages() {
    when(context.getCurrentFlowId()).thenReturn(APPLICATION_FLOW_ID);
    when(flowStageService.findByFlowIdAndStatuses(APPLICATION_FLOW_ID, Set.of(IN_PROGRESS, FAILED)))
      .thenReturn(List.of());

    stage.execute(context);

    verify(flowStageService).findByFlowIdAndStatuses(APPLICATION_FLOW_ID, Set.of(IN_PROGRESS, FAILED));
  }

  private static FlowStage stage(UUID id, String name, ExecutionStatus status) {
    return new FlowStage().id(id).flowId(APPLICATION_FLOW_ID).name(name).status(status);
  }
}
