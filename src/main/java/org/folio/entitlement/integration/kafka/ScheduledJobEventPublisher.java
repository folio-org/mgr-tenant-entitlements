package org.folio.entitlement.integration.kafka;

import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.integration.kafka.KafkaUtils.getTenantTopicName;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.entitlement.integration.folio.ApplicationStageContext;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduledJobEventPublisher extends DatabaseLoggingStage<ApplicationStageContext> {

  private static final String SCHEDULED_JOB_TOPIC = "mgr-tenant-entitlements.scheduled-job";
  private final KafkaEventPublisher kafkaEventPublisher;

  @Override
  public void execute(ApplicationStageContext context) {
    var tenantId = context.getTenantId().toString();
    var tenantName = context.getTenantName();
    var moduleDescriptors = context.getApplicationDescriptor().getModuleDescriptors();
    var topicName = getTenantTopicName(SCHEDULED_JOB_TOPIC, tenantName);

    moduleDescriptors.stream()
      .flatMap(appDescriptor -> toStream(appDescriptor.getProvides()))
      .filter(ScheduledJobEventPublisher::isScheduledHandler)
      .flatMap(providedInterfaces -> toStream(providedInterfaces.getHandlers()))
      .map(handler -> createEvent(handler, tenantName))
      .forEach(scheduledJobEvent -> kafkaEventPublisher.send(topicName, tenantId, scheduledJobEvent));
  }

  private static boolean isScheduledHandler(InterfaceDescriptor handler) {
    return Objects.equals("system", handler.getInterfaceType()) && Objects.equals(handler.getId(), "_timer");
  }

  private static ResourceEvent<RoutingEntry> createEvent(RoutingEntry routingEntry, String tenantId) {
    return ResourceEvent.<RoutingEntry>builder()
      .tenant(tenantId)
      .type(CREATE)
      .resourceName("Scheduled Job")
      .newValue(routingEntry)
      .build();
  }
}
