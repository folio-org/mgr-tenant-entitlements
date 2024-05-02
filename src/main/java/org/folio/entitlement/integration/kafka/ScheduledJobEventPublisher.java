package org.folio.entitlement.integration.kafka;

import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.integration.kafka.ScheduledJobModuleEventPublisher.SCHEDULED_JOB_TOPIC;
import static org.folio.entitlement.integration.kafka.ScheduledJobModuleEventPublisher.getScheduledTimers;
import static org.folio.integration.kafka.KafkaUtils.getTenantTopicName;

import java.util.Optional;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.ModuleDescriptorHolder;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.kafka.model.ScheduledTimers;
import org.folio.entitlement.integration.okapi.model.OkapiStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ScheduledJobEventPublisher extends DatabaseLoggingStage<OkapiStageContext> {

  private final KafkaEventPublisher kafkaEventPublisher;

  @Override
  public void execute(OkapiStageContext context) {
    var tenantName = context.getTenantName();
    var entitledApplicationId = context.getEntitledApplicationId();
    var timerEventsStream = getTimerEventsStream(context);
    var deprecatedTimerEventsStream = toStream(context.getDeprecatedModuleDescriptors())
      .map(moduleDescriptor -> getEvent(moduleDescriptor, tenantName, entitledApplicationId, true))
      .flatMap(Optional::stream);

    var messageKey = context.getTenantId().toString();
    var kafkaTopic = getTenantTopicName(SCHEDULED_JOB_TOPIC, tenantName);
    Stream.concat(timerEventsStream, deprecatedTimerEventsStream)
      .forEach(event -> kafkaEventPublisher.send(kafkaTopic, messageKey, event));
  }

  private static Stream<ResourceEvent<ScheduledTimers>> getTimerEventsStream(OkapiStageContext context) {
    var tenantName = context.getTenantName();
    var applicationId = context.getApplicationId();
    var request = context.getEntitlementRequest();
    if (request.getType() == UPGRADE) {
      return toStream(context.getModuleDescriptorHolders())
        .map(moduleDescriptorHolder -> getEvent(moduleDescriptorHolder, context))
        .flatMap(Optional::stream);
    }

    return toStream(context.getModuleDescriptors())
      .map(moduleDescriptor -> getEvent(moduleDescriptor, tenantName, applicationId, false))
      .flatMap(Optional::stream);
  }

  private static Optional<ResourceEvent<ScheduledTimers>> getEvent(ModuleDescriptorHolder mdh, OkapiStageContext ctx) {
    var tenantName = ctx.getTenantName();
    var scheduledTimers = getScheduledTimers(ctx.getApplicationId(), mdh.moduleDescriptor());
    var oldScheduledTimers = getScheduledTimers(ctx.getEntitledApplicationId(), mdh.installedModuleDescriptor());
    return ScheduledJobModuleEventPublisher.createEvent(tenantName, scheduledTimers, oldScheduledTimers);
  }

  private static Optional<ResourceEvent<ScheduledTimers>> getEvent(ModuleDescriptor moduleDescriptor,
    String tenantName, String applicationId, boolean isDeprecated) {
    var scheduledTimers = getScheduledTimers(applicationId, moduleDescriptor);
    return isDeprecated
      ? ScheduledJobModuleEventPublisher.createEvent(tenantName, null, scheduledTimers)
      : ScheduledJobModuleEventPublisher.createEvent(tenantName, scheduledTimers, null);
  }
}
