package org.folio.entitlement.integration.kafka;

import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;

import java.util.Optional;
import java.util.stream.Stream;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.ModuleDescriptorHolder;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.okapi.model.OkapiStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractEventPublisher<T> extends DatabaseLoggingStage<OkapiStageContext> {

  protected KafkaEventPublisher kafkaEventPublisher;

  @Override
  public void execute(OkapiStageContext context) {
    var tenantName = context.getTenantName();
    var entitledApplicationId = context.getEntitledApplicationId();
    var resourceEventsStream = getResourceEventsStream(context);
    var deprecatedResourceEventsStream = toStream(context.getDeprecatedModuleDescriptors())
      .map(moduleDescriptor -> getEvent(moduleDescriptor, tenantName, entitledApplicationId, true))
      .flatMap(Optional::stream);

    var messageKey = context.getTenantId().toString();
    var kafkaTopic = getTopicName(tenantName);
    Stream.concat(resourceEventsStream, deprecatedResourceEventsStream)
      .forEach(event -> kafkaEventPublisher.send(kafkaTopic, messageKey, event));
  }

  @Autowired
  public void setKafkaEventPublisher(KafkaEventPublisher kafkaEventPublisher) {
    this.kafkaEventPublisher = kafkaEventPublisher;
  }

  /**
   * Creates event payload from application id and module descriptor.
   *
   * @param applicationId - application identifier as {@link String}
   * @param moduleDescriptor - module descriptor as {@link ModuleDescriptor}
   * @return {@link Optional} of created event payload, empty if event payload not provided
   */
  protected abstract Optional<T> getEventPayload(String applicationId, ModuleDescriptor moduleDescriptor);

  /**
   * Creates topic name using tenant name.
   *
   * @param tenantName - tenant name as {@link String}
   * @return kafka topic name
   */
  protected abstract String getTopicName(String tenantName);

  /**
   * Returns resource name for {@link ResourceEvent} object.
   *
   * @return resource name
   */
  protected abstract String getResourceName();

  private Stream<ResourceEvent<T>> getResourceEventsStream(OkapiStageContext context) {
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

  private Optional<ResourceEvent<T>> getEvent(ModuleDescriptorHolder mdh, OkapiStageContext ctx) {
    var tenantName = ctx.getTenantName();
    var newEventPayload = getEventPayload(ctx.getApplicationId(), mdh.moduleDescriptor()).orElse(null);
    var oldEventPayload = getEventPayload(ctx.getEntitledApplicationId(), mdh.installedModuleDescriptor()).orElse(null);
    return createEvent(tenantName, newEventPayload, oldEventPayload);
  }

  private Optional<ResourceEvent<T>> getEvent(ModuleDescriptor moduleDescriptor,
    String tenantName, String applicationId, boolean isDeprecated) {
    var payload = getEventPayload(applicationId, moduleDescriptor).orElse(null);
    return isDeprecated ? createEvent(tenantName, null, payload) : createEvent(tenantName, payload, null);
  }

  private Optional<ResourceEvent<T>> createEvent(String tenantName, T newPayload, T oldPayload) {
    return KafkaEventUtils.createEvent(getResourceName(), tenantName, newPayload, oldPayload);
  }
}
