package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.domain.dto.ExecutionStatus.IN_PROGRESS;
import static org.folio.integration.kafka.model.ResourceResultStatus.FAILURE;
import static org.folio.integration.kafka.model.ResourceResultStatus.SUCCESS;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.service.FlowStageService;
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.folio.entitlement.service.flow.FlowService;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.integration.kafka.model.ResourceResultStatus;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResourceResultEventService {

  private final FlowStageService stageService;
  private final ApplicationFlowService applicationFlowService;
  private final FlowService flowService;

  private final Map<ResourceResultStatus, BiConsumer<FlowStage, ResourceResultEvent>> statusHandlers = Map.of(
    SUCCESS, this::applySuccessResult,
    FAILURE, this::applyFailureResult
  );

  @Transactional
  public void processEvent(@Valid ResourceResultEvent event) {
    var stageId = UUID.fromString(event.getId());

    log.info("Processing resource result event: {}", () -> eventToString(event));

    stageService.findById(stageId).ifPresentOrElse(
      applyStageResult(event),
      () -> log.info("Flow stage is not found by id for resource result event: id = {}, event = {}",
        stageId, event)
    );
  }

  private Consumer<FlowStage> applyStageResult(ResourceResultEvent event) {
    return stage -> {
      if (stage.getStatus() != IN_PROGRESS) {
        // stage status is not in_progress
        // Ignore. Duplicate delivery, or already resolved.
        log.info("Flow stage status is not 'In Progress' for resource result event: flowStage = {}, event = {}."
            + " Event is ignored..", () -> flowStageToString(stage), () -> eventToString(event));
        return;
      }

      getHandler(event.getStatus()).accept(stage, event);
    };
  }

  private BiConsumer<FlowStage, ResourceResultEvent> getHandler(ResourceResultStatus status) {
    var result = statusHandlers.get(status);
    if (result == null) {
      throw new IllegalStateException("Unexpected value: " + status);
    }
    return result;
  }

  private void applySuccessResult(FlowStage stage, ResourceResultEvent result) {
    var finishedAt = ZonedDateTime.now();

    updateAndThen(
      () -> stageService.finishActiveStage(stage.getId(), finishedAt),
      () -> log.info("Flow stage marked as 'Finished' for resource result event: flowStage = {}, event = {}",
          () -> flowStageToString(stage), () -> eventToString(result)));

    finishFlows(stage, result, finishedAt);
  }

  private void applyFailureResult(FlowStage stage, ResourceResultEvent result) {
    var finishedAt = ZonedDateTime.now();

    updateAndThen(
      () -> stageService.failActiveStage(stage.getId(), result.getDetails(), finishedAt),
      () -> log.info("Flow stage marked as 'Failed' for resource result event: flowStage = {}, event = {}",
        () -> flowStageToString(stage), () -> eventToString(result)));

    failFlows(stage, result, finishedAt);
  }

  private void finishFlows(FlowStage stage, ResourceResultEvent result, ZonedDateTime finishedAt) {
    // If no in_progress stages remain for the application flow, and it is in_progress → finished.
    var stageFlowId = stage.getFlowId();
    updateAndThen(
      () -> applicationFlowService.finishFlowIfNoActiveStages(stageFlowId, finishedAt),
      () -> log.debug("Application flow marked as finished for resource result event: flowId = {}, eventId = {}",
          () -> stageFlowId, result::getId));

    // Then, if nothing remains pending for the top-level flow, and it is in_progress → finished.
    var flow = flowService.getTopLevelFlow(stageFlowId);
    updateAndThen(
      () -> flowService.finishFlowIfNoActiveStages(flow.getId(), finishedAt),
      () -> log.debug("Top-level flow marked as finished for resource result event: flowId = {}, eventId = {}",
          flow::getId, result::getId));
  }

  private void failFlows(FlowStage stage, ResourceResultEvent result, ZonedDateTime finishedAt) {
    // Application flow → failed if in_progress.
    var stageFlowId = stage.getFlowId();
    updateAndThen(
      () -> applicationFlowService.failActiveFlow(stageFlowId, finishedAt),
      () -> log.debug("Application flow marked as 'Failed' for resource result event: flowId = {}, eventId = {}",
        () -> stageFlowId, result::getId));

    // Top-level flow → failed if in_progress.
    var topLevelFlow = flowService.getTopLevelFlow(stageFlowId);
    updateAndThen(
      () -> flowService.failActiveFlow(topLevelFlow.getId(), finishedAt),
      () -> log.debug("Top-level flow marked as 'Failed' for resource result event: flowId = {}, eventId = {}",
        topLevelFlow::getId, result::getId));
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

  private static void updateAndThen(Supplier<Integer> update, Runnable then) {
    var updated = update.get();
    if (updated > 0) {
      then.run();
    }
  }
}
