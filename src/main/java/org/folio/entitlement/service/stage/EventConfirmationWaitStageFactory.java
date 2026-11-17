package org.folio.entitlement.service.stage;

import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.configuration.EventConfirmationWaitProperties;
import org.folio.entitlement.repository.FlowStageRepository;
import org.folio.entitlement.service.FlowStageService;
import org.springframework.stereotype.Component;

/**
 * Creates {@link EventConfirmationWaitStage} instances wired with the beans stages need.
 */
@Component
@RequiredArgsConstructor
public class EventConfirmationWaitStageFactory {

  private final EventConfirmationWaitProperties properties;
  private final FlowStageService flowStageService;
  private final FlowStageRepository flowStageRepository;
  private final ThreadLocalModuleStageContext threadLocalModuleStageContext;

  /**
   * Creates a stage that waits for confirmations of events published by the given publishers.
   *
   * @param phaseName - unique name of the wait within a flow
   * @param publishers - publisher stage classes whose events must be confirmed
   * @return created {@link EventConfirmationWaitStage}
   */
  public EventConfirmationWaitStage create(String phaseName,
    Collection<Class<? extends ModuleDatabaseLoggingStage>> publishers) {
    var stage = new EventConfirmationWaitStage(phaseName, publishers, properties, flowStageService);
    stage.setStageRepository(flowStageRepository);
    stage.setThreadLocalModuleStageContext(threadLocalModuleStageContext);
    return stage;
  }
}
