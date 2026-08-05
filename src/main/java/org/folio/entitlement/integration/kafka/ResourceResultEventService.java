package org.folio.entitlement.integration.kafka;

import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import java.util.UUID;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.folio.entitlement.domain.entity.FlowStageEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.repository.FlowStageRepository;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class ResourceResultEventService {

  private final FlowStageRepository flowStageRepository;

  @Transactional
  public void processEvent(@Valid ResourceResultEvent event) {
    var stageId = UUID.fromString(event.getId());

    log.info("Processing resource result event: {}", () -> eventToString(event));

    flowStageRepository.findByStageId(stageId).ifPresentOrElse(
      applyFlowResult(event),
      () -> log.info("Flow stage is not found by id for resource result event: id = {}, event = {}",
        stageId, event)
    );
  }

  private Consumer<FlowStageEntity> applyFlowResult(ResourceResultEvent event) {
    return flowStage -> {
      if (flowStage.getStatus() != EntityExecutionStatus.IN_PROGRESS) {
        // stage status is not in_progress
        // Ignore. Duplicate delivery, or already resolved.
        log.info("Flow stage status is not 'In Progress' for resource result event: flowStage = {}, event = {}."
            + " Event ignored..", () -> flowStageToString(flowStage), () -> eventToString(event));
        return;
      }

      switch (event.getStatus()) {
        case SUCCESS -> {
          // Stage → finished.
          // If no in_progress stages remain for the application flow and it is in_progress → finished.
          // Then, if nothing remains pending for the top-level flow and it is in_progress → finished.
        }
        case FAILURE -> {
          // Stage → failed, with details written to error_message and a constant to error_type.
          // Application flow → failed if in_progress.
          // Top-level flow → failed if in_progress.
        }
        default -> throw new IllegalStateException("Unexpected value: " + event.getStatus());
      }
    };
  }

  private static String flowStageToString(FlowStageEntity flowStage) {
    return new ToStringBuilder(flowStage)
      .append("id", flowStage.getId())
      .append("flowId", flowStage.getFlowId())
      .append("stageName", flowStage.getStageName())
      .append("status", flowStage.getStatus())
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
