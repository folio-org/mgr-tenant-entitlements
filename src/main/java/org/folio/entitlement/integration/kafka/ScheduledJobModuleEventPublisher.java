package org.folio.entitlement.integration.kafka;

import static java.util.Collections.emptyList;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.getResourceEventType;
import static org.folio.integration.kafka.KafkaUtils.getTenantTopicName;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.kafka.model.ScheduledTimers;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduledJobModuleEventPublisher extends ModuleDatabaseLoggingStage {

  public static final String SCHEDULED_JOB_TOPIC = "mgr-tenant-entitlements.scheduled-job";
  private final KafkaEventPublisher kafkaEventPublisher;

  @Override
  public void execute(ModuleStageContext ctx) {
    var tenantName = ctx.getTenantName();
    var newScheduledTimers = getScheduledTimers(ctx.getApplicationId(), ctx.getModuleDescriptor());
    var oldScheduledTimers = getScheduledTimers(ctx.getEntitledApplicationId(), ctx.getInstalledModuleDescriptor());
    var topicName = getTenantTopicName(SCHEDULED_JOB_TOPIC, tenantName);

    createEvent(tenantName, newScheduledTimers, oldScheduledTimers).ifPresent(event ->
      kafkaEventPublisher.send(topicName, ctx.getTenantId().toString(), event));
  }

  /**
   * Creates {@link ScheduledTimers} event, nullable.
   *
   * @param applicationId - application identifier, nullable
   * @param moduleDescriptor - module descriptor, nullable
   * @return {@link Optional} of created {@link ScheduledTimers}
   */
  public static ScheduledTimers getScheduledTimers(String applicationId, ModuleDescriptor moduleDescriptor) {
    var timerRoutingEntries = getTimerRoutingEntries(moduleDescriptor);
    if (isEmpty(timerRoutingEntries)) {
      return null;
    }

    return ScheduledTimers.of(moduleDescriptor.getId(), applicationId, timerRoutingEntries);
  }

  /**
   * Creates {@link ResourceEvent} object for given tenant nane, new and old event bodies.
   *
   * @param tenantName - tenant name as {@link String}
   * @param newScheduledTimers - new value in {@link ResourceEvent}
   * @param oldScheduledTimers - old value in {@link ResourceEvent}
   * @return {@link Optional} of {@link ResourceEvent}, it will be empty if old and new values are not valid
   */
  public static Optional<ResourceEvent<ScheduledTimers>> createEvent(String tenantName,
    ScheduledTimers newScheduledTimers, ScheduledTimers oldScheduledTimers) {
    if (newScheduledTimers == null && oldScheduledTimers == null) {
      return Optional.empty();
    }

    var scheduledJobEvent = ResourceEvent.<ScheduledTimers>builder()
      .tenant(tenantName)
      .type(getResourceEventType(newScheduledTimers, oldScheduledTimers))
      .resourceName("Scheduled Job")
      .newValue(newScheduledTimers)
      .oldValue(oldScheduledTimers)
      .build();

    return Optional.of(scheduledJobEvent);
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
