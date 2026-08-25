package org.folio.entitlement.integration.kafka;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.folio.entitlement.domain.dto.ExecutionStatus.IN_PROGRESS;

import jakarta.validation.Valid;
import java.time.ZonedDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.service.FlowStageService;
import org.folio.entitlement.service.flow.FlowCompletionService;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.integration.kafka.model.ResourceResultStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResourceResultEventService {

  /**
   * Error type recorded on a stage failed by a downstream service, so it can be told apart from a stage that
   * failed locally and from one failed by the async confirmation timeout.
   */
  static final String ASYNC_FAILURE_ERROR_TYPE = "ResourceResultFailure";

  private static final String NO_DETAILS_MESSAGE = "Downstream service reported a failure with no details";

  private final FlowStageService stageService;
  private final FlowCompletionService flowCompletionService;

  /**
   * Applies a downstream result to the flow stage that produced the originating event.
   *
   * <p>Structurally invalid events are dropped rather than rethrown: the listener commits the offset and the
   * record does not go round the container's retry loop, because no number of retries can make a malformed id or
   * a missing status valid.</p>
   *
   * @param event - inbound {@link ResourceResultEvent}
   */
  @Transactional
  public void processEvent(@Valid ResourceResultEvent event) {
    log.info("Processing resource result event: {}", () -> eventToString(event));

    var status = event.getStatus();
    if (status == null) {
      // ResourceResultEvent declares no constraint on status, so @Valid admits this.
      log.warn("Resource result event has no status, event is ignored: {}", () -> eventToString(event));
      return;
    }

    var stageId = parseStageId(event);
    if (stageId == null) {
      return;
    }

    stageService.findById(stageId).ifPresentOrElse(
      stage -> applyStageResult(stage, event, status),
      () -> log.warn("Flow stage is not found by id, resource result event is ignored: stageId = {}, event = {}",
        stageId, eventToString(event)));
  }

  private void applyStageResult(FlowStage stage, ResourceResultEvent event, ResourceResultStatus status) {
    if (stage.getStatus() != IN_PROGRESS) {
      // Cheap fast path; the compare-and-set below is the authority. Duplicate or late delivery.
      log.info("Flow stage is not 'In Progress', resource result event is ignored: flowStage = {}, event = {}",
        () -> flowStageToString(stage), () -> eventToString(event));
      return;
    }

    switch (status) {
      case SUCCESS -> applySuccessResult(stage, event);
      case FAILURE -> applyFailureResult(stage, event);
      // Not unreachable in practice: a new constant added upstream would land here rather than throwing
      // from inside the listener.
      default -> log.warn("Unsupported resource result status, event is ignored: status = {}, event = {}",
        status, eventToString(event));
    }
  }

  private void applySuccessResult(FlowStage stage, ResourceResultEvent result) {
    var finishedAt = ZonedDateTime.now();

    if (stageService.finishActiveStage(stage.getId(), finishedAt) == 0) {
      // Lost the compare-and-set: a concurrent delivery of this result already resolved the stage. Returning here
      // is what keeps the losing handler from attempting flow completion on the winner's behalf.
      log.info("Flow stage was resolved concurrently, resource result event is ignored: flowStage = {}, event = {}",
        () -> flowStageToString(stage), () -> eventToString(result));
      return;
    }

    log.info("Flow stage marked as 'Finished' for resource result event: flowStage = {}, event = {}",
      () -> flowStageToString(stage), () -> eventToString(result));

    flowCompletionService.completeIfNoActiveStages(stage.getFlowId(), finishedAt);
  }

  private void applyFailureResult(FlowStage stage, ResourceResultEvent result) {
    var finishedAt = ZonedDateTime.now();
    var details = defaultIfBlank(result.getDetails(), NO_DETAILS_MESSAGE);

    if (stageService.failActiveStage(stage.getId(), ASYNC_FAILURE_ERROR_TYPE, details, finishedAt) == 0) {
      log.info("Flow stage was resolved concurrently, resource result event is ignored: flowStage = {}, event = {}",
        () -> flowStageToString(stage), () -> eventToString(result));
      return;
    }

    log.warn("Flow stage marked as 'Failed' for resource result event: flowStage = {}, event = {}",
      () -> flowStageToString(stage), () -> eventToString(result));

    flowCompletionService.failFlows(stage.getFlowId(), finishedAt);
  }

  private static UUID parseStageId(ResourceResultEvent event) {
    try {
      return UUID.fromString(event.getId());
    } catch (IllegalArgumentException e) {
      // id is validated as @NotBlank only, so a non-UUID value reaches this point.
      log.warn("Resource result event id is not a valid UUID, event is ignored: {}", eventToString(event));
      return null;
    }
  }

  private static String flowStageToString(FlowStage stage) {
    return new ToStringBuilder(stage)
      .append("id", stage.getId())
      .append("flowId", stage.getFlowId())
      .append("name", stage.getName())
      .append("status", stage.getStatus())
      .toString();
  }

  private static String eventToString(ResourceResultEvent event) {
    return new ToStringBuilder(event)
      .append("id", event.getId())
      .append("tenant", event.getTenant())
      .append("moduleId", event.getModuleId())
      .append("resource", event.getResourceName())
      .append("status", event.getStatus())
      .toString();
  }
}
