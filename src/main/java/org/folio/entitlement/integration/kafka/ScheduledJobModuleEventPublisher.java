package org.folio.entitlement.integration.kafka;

import static java.util.Collections.emptyList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.SCHEDULED_JOB_RESOURCE_NAME;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.SCHEDULED_JOB_TOPIC;
import static org.folio.integration.kafka.KafkaUtils.getTenantTopicName;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.entitlement.integration.kafka.model.ScheduledTimers;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduledJobModuleEventPublisher extends AbstractModuleEventPublisher<ScheduledTimers> {

  @Override
  protected Optional<ScheduledTimers> getEventPayload(String applicationId, ModuleDescriptor moduleDescriptor) {
    return getScheduledTimers(applicationId, moduleDescriptor);
  }

  @Override
  protected String getTopicName(String tenantName) {
    return getTenantTopicName(SCHEDULED_JOB_TOPIC, tenantName);
  }

  @Override
  protected String getResourceName() {
    return SCHEDULED_JOB_RESOURCE_NAME;
  }

  /**
   * Creates {@link ScheduledTimers} event, nullable.
   *
   * @param applicationId - application identifier, nullable
   * @param moduleDescriptor - module descriptor, nullable
   * @return {@link Optional} of created {@link ScheduledTimers}
   */
  public static Optional<ScheduledTimers> getScheduledTimers(String applicationId, ModuleDescriptor moduleDescriptor) {
    var timerRoutingEntries = getTimerRoutingEntries(moduleDescriptor);
    if (isEmpty(timerRoutingEntries)) {
      return Optional.empty();
    }

    var scheduledTimers = ScheduledTimers.of(moduleDescriptor.getId(), applicationId, timerRoutingEntries);
    return Optional.ofNullable(scheduledTimers);
  }

  private static List<RoutingEntry> getTimerRoutingEntries(ModuleDescriptor moduleDescriptor) {
    if (moduleDescriptor == null) {
      return emptyList();
    }

    return toStream(moduleDescriptor.getProvides())
      .filter(ScheduledJobModuleEventPublisher::isScheduledHandler)
      .flatMap(interfaceDescriptor -> toStream(interfaceDescriptor.getHandlers()))
      .toList();
  }

  private static boolean isScheduledHandler(InterfaceDescriptor handler) {
    return Objects.equals("system", handler.getInterfaceType()) && Objects.equals(handler.getId(), "_timer");
  }
}
