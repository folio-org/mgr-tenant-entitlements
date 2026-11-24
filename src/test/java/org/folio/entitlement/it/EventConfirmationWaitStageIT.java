package org.folio.entitlement.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.folio.entitlement.service.stage.EventConfirmationWaitStage.TIMEOUT_ERROR_TYPE;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.AFTER_TEST_METHOD;
import static org.springframework.test.context.jdbc.SqlMergeMode.MergeMode.MERGE;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.folio.entitlement.configuration.EventConfirmationWaitProperties;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.integration.kafka.CapabilitiesModuleEventPublisher;
import org.folio.entitlement.integration.kafka.SystemUserModuleEventPublisher;
import org.folio.entitlement.service.FlowStageService;
import org.folio.entitlement.service.stage.EventConfirmationWaitStage;
import org.folio.entitlement.support.base.BaseIntegrationTest;
import org.folio.test.types.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlMergeMode;

/**
 * Runs {@link EventConfirmationWaitStage} against the real database, with confirmations applied from another thread
 * like the {@code resource-result} listener does.
 */
@IntegrationTest
@SqlMergeMode(MERGE)
@Sql(executionPhase = AFTER_TEST_METHOD, scripts = "classpath:/sql/truncate-tables.sql")
@Sql("classpath:/sql/event-confirmation-wait/phase-flow.sql")
@TestPropertySource(properties = {
  "application.apigw.enabled=false",
  "application.keycloak.enabled=false",
  "application.event-confirmation-wait.enabled=true",
  "application.event-confirmation-wait.poll-interval=50ms",
  "application.event-confirmation-wait.max-wait=2s",
})
class EventConfirmationWaitStageIT extends BaseIntegrationTest {

  private static final UUID APPLICATION_FLOW_ID = UUID.fromString("bb000000-0000-0000-0000-000000000011");
  private static final String WAIT_STAGE_NAME = "EventConfirmationWait-phase1";
  private static final String CAPABILITY_EVENT = "cc000000-0000-0000-0000-00000000000a";
  private static final String SYSTEM_USER_EVENT = "cc000000-0000-0000-0000-00000000000b";
  private static final String SCHEDULED_JOB_EVENT = "cc000000-0000-0000-0000-00000000000c";
  private static final String INSTALLER_EVENT = "cc000000-0000-0000-0000-00000000000d";

  @Autowired private EventConfirmationWaitStage stage;
  @Autowired private JdbcTemplate jdbcTemplate;

  private ApplicationStageContext context;
  private ScheduledExecutorService scheduler;

  @BeforeEach
  void setUp() {
    scheduler = Executors.newSingleThreadScheduledExecutor();
    context = mock(ApplicationStageContext.class);
    when(context.getCurrentFlowId()).thenReturn(APPLICATION_FLOW_ID);
  }

  @AfterEach
  void tearDown() {
    scheduler.shutdownNow();
  }

  @Test
  void execute_positive_completesWhenEveryWatchedEventIsConfirmedFromAnotherThread() {
    confirmLater(500, "FINISHED", CAPABILITY_EVENT, SYSTEM_USER_EVENT);

    assertThatCode(() -> stage.execute(context)).doesNotThrowAnyException();

    assertThat(statusOf(CAPABILITY_EVENT)).isEqualTo("FINISHED");
    assertThat(statusOf(SYSTEM_USER_EVENT)).isEqualTo("FINISHED");
  }

  @Test
  void execute_negative_failureConfirmationFailsTheStageBeforeTimeout() {
    confirmLater(300, "FAILED", SYSTEM_USER_EVENT);
    confirmLater(300, "FINISHED", CAPABILITY_EVENT);

    var started = System.nanoTime();
    assertThatThrownBy(() -> stage.execute(context))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Event processing failed")
      .hasMessageContaining("systemUserModuleEventPublisher");

    assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(1800);
  }

  @Test
  void execute_negative_timeoutFailsUnconfirmedEventsAndLeavesOthersUntouched() {
    confirmLater(100, "FINISHED", CAPABILITY_EVENT);

    assertThatThrownBy(() -> stage.execute(context))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Events are not confirmed")
      .hasMessageContaining("eventId=" + SYSTEM_USER_EVENT)
      .hasMessageNotContaining("eventId=" + CAPABILITY_EVENT);

    assertThat(statusOf(CAPABILITY_EVENT)).isEqualTo("FINISHED");
    assertThat(statusOf(SYSTEM_USER_EVENT)).isEqualTo("FAILED");
    assertThat(errorTypeOf(SYSTEM_USER_EVENT)).isEqualTo(TIMEOUT_ERROR_TYPE);

    // a publisher outside the phase and an unrelated stage are neither waited for nor failed
    assertThat(statusOf(SCHEDULED_JOB_EVENT)).isEqualTo("IN_PROGRESS");
    assertThat(statusOf(INSTALLER_EVENT)).isEqualTo("IN_PROGRESS");
  }

  @Test
  void execute_negative_lateConfirmationAfterTimeoutIsNotApplied() {
    assertThatThrownBy(() -> stage.execute(context)).isInstanceOf(IllegalStateException.class);

    var updated = jdbcTemplate.update(
      "UPDATE flow_stage SET status = 'FINISHED' WHERE stage_id = ?::uuid AND status = 'IN_PROGRESS'",
      SYSTEM_USER_EVENT);

    assertThat(updated).isZero();
    assertThat(statusOf(SYSTEM_USER_EVENT)).isEqualTo("FAILED");
  }

  @Test
  void lifecycle_positive_waitStageIsRecordedAsFinished() {
    confirmLater(100, "FINISHED", CAPABILITY_EVENT, SYSTEM_USER_EVENT);

    stage.onStart(context);
    stage.execute(context);
    stage.onSuccess(context);

    assertThat(waitStageStatus()).isEqualTo("FINISHED");
  }

  @Test
  void lifecycle_negative_waitStageIsRecordedAsFailedWithTimeoutMessage() {
    stage.onStart(context);
    var error = catchThrowable(() -> stage.execute(context));
    stage.onError(context, (Exception) error);

    assertThat(waitStageStatus()).isEqualTo("FAILED");
    assertThat(jdbcTemplate.queryForObject(
      "SELECT error_message FROM flow_stage WHERE stage = ?", String.class, WAIT_STAGE_NAME))
      .contains("Events are not confirmed");
  }

  private String waitStageStatus() {
    return jdbcTemplate.queryForObject("SELECT status::text FROM flow_stage WHERE stage = ?", String.class,
      WAIT_STAGE_NAME);
  }

  private String statusOf(String eventId) {
    return jdbcTemplate.queryForObject("SELECT status::text FROM flow_stage WHERE stage_id = ?::uuid",
      String.class, eventId);
  }

  private String errorTypeOf(String eventId) {
    return jdbcTemplate.queryForObject("SELECT error_type FROM flow_stage WHERE stage_id = ?::uuid",
      String.class, eventId);
  }

  /**
   * Sets the status of the given events after a delay, in a separate thread and transaction.
   */
  private void confirmLater(long delayMillis, String status, String... eventIds) {
    scheduler.schedule(() -> {
      for (var eventId : eventIds) {
        jdbcTemplate.update("UPDATE flow_stage SET status = '" + status + "' WHERE stage_id = ?::uuid", eventId);
      }
    }, delayMillis, TimeUnit.MILLISECONDS);
  }

  /**
   * Declares a wait for one phase as a bean, so it is injected and proxied like every other stage.
   */
  @TestConfiguration
  static class WaitStageConfiguration {

    @Bean
    EventConfirmationWaitStage eventConfirmationWaitPhase1(EventConfirmationWaitProperties properties,
      FlowStageService flowStageService) {
      return new EventConfirmationWaitStage(WAIT_STAGE_NAME,
        List.of(SystemUserModuleEventPublisher.class, CapabilitiesModuleEventPublisher.class), properties,
        flowStageService);
    }
  }
}
