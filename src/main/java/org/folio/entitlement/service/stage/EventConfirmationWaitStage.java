package org.folio.entitlement.service.stage;

import static java.lang.String.join;
import static java.time.ZonedDateTime.now;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.lang3.StringUtils.uncapitalize;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;

import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.configuration.EventConfirmationWaitProperties;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.service.FlowStageService;

/**
 * Holds an application flow until every event published by the given publisher stages is confirmed as processed.
 *
 * <p>Each publisher records one {@code flow_stage} row per event, and that row's stage id is the event id the
 * consumer echoes back in its confirmation. When the publishers are configured to await completion the rows stay
 * {@code IN_PROGRESS} until the confirmation is applied, so "all confirmed" is "no matching row is in progress".
 * A {@code FAILED} row fails this stage at once; rows still in progress when the maximum wait elapses are failed and
 * named in the error, so a late confirmation is dropped by the rule for stages that are no longer in progress.</p>
 *
 * <p>The stage is a no-op when {@code application.event-confirmation-wait.enabled} is false.</p>
 */
@Log4j2
public class EventConfirmationWaitStage extends DatabaseLoggingStage<ApplicationStageContext> {

  public static final String TIMEOUT_ERROR_TYPE = "EventConfirmationTimeout";

  private static final Set<EntityExecutionStatus> WATCHED_STATUSES = Set.of(IN_PROGRESS, FAILED);

  private final String phaseName;
  private final Set<String> stageNameSuffixes;
  private final EventConfirmationWaitProperties properties;
  private final FlowStageService flowStageService;

  /**
   * Creates the wait stage.
   *
   * @param phaseName - unique name of the wait within a flow, used as stage name
   * @param publishers - publisher stage classes whose events must be confirmed
   * @param properties - wait settings
   * @param flowStageService - flow stage service
   */
  public EventConfirmationWaitStage(String phaseName,
    Collection<Class<? extends ModuleDatabaseLoggingStage>> publishers, EventConfirmationWaitProperties properties,
    FlowStageService flowStageService) {
    this.phaseName = phaseName;
    this.stageNameSuffixes = publishers.stream().map(c -> "-" + uncapitalize(c.getSimpleName())).collect(toSet());
    this.properties = properties;
    this.flowStageService = flowStageService;
  }

  @Override
  public String getId() {
    return phaseName;
  }

  @Override
  public void execute(ApplicationStageContext context) {
    if (!properties.isEnabled()) {
      log.debug("Event confirmation wait is disabled, stage is skipped: {}", phaseName);
      return;
    }

    var flowId = context.getCurrentFlowId();
    var deadline = System.nanoTime() + properties.getMaxWait().toNanos();

    while (true) {
      var stages = findPhaseStages(flowId);
      throwIfAnyFailed(stages);

      var unconfirmed = stages.stream().filter(s -> s.getStatus() == ExecutionStatus.IN_PROGRESS).toList();
      if (unconfirmed.isEmpty()) {
        log.info("All published events are confirmed [phase: {}, flowId: {}]", phaseName, flowId);
        return;
      }

      if (System.nanoTime() - deadline >= 0) {
        failUnconfirmed(unconfirmed);
      }

      sleep();
    }
  }

  private List<FlowStage> findPhaseStages(UUID flowId) {
    return flowStageService.findByFlowIdAndStatuses(flowId, WATCHED_STATUSES).stream()
      .filter(s -> stageNameSuffixes.stream().anyMatch(suffix -> s.getName().endsWith(suffix)))
      .toList();
  }

  private void throwIfAnyFailed(List<FlowStage> stages) {
    var failed = stages.stream().filter(s -> s.getStatus() == ExecutionStatus.FAILED).toList();
    if (!failed.isEmpty()) {
      var details = failed.stream().map(s -> describe(s) + ": " + s.getErrorMessage()).toList();
      throw new IllegalStateException("Event processing failed [phase: " + phaseName + "]: " + join("; ", details));
    }
  }

  private void failUnconfirmed(List<FlowStage> unconfirmed) {
    var names = unconfirmed.stream().map(EventConfirmationWaitStage::describe).toList();
    var message = "Events are not confirmed within " + properties.getMaxWait() + " [phase: " + phaseName
      + ", unconfirmed events: " + join(", ", names) + "]";

    var finishedAt = now(ZoneId.systemDefault());
    unconfirmed.forEach(s -> flowStageService.failActiveStage(s.getId(), TIMEOUT_ERROR_TYPE, message, finishedAt));
    throw new IllegalStateException(message);
  }

  private void sleep() {
    try {
      Thread.sleep(properties.getPollInterval().toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for event confirmations: " + phaseName, e);
    }
  }

  private static String describe(FlowStage stage) {
    return stage.getName() + " (eventId=" + stage.getId() + ")";
  }
}
