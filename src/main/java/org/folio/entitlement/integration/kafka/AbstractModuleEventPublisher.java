package org.folio.entitlement.integration.kafka;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor
public abstract class AbstractModuleEventPublisher<T> extends ModuleDatabaseLoggingStage {

  protected KafkaEventPublisher kafkaEventPublisher;

  @Override
  public void execute(ModuleStageContext ctx) {
    var tenant = ctx.getTenantName();
    var newPayload = getEventPayload(ctx.getApplicationId(), ctx.getModuleDescriptor()).orElse(null);
    var oldPayload = getEventPayload(ctx.getEntitledApplicationId(), ctx.getInstalledModuleDescriptor()).orElse(null);

    var topicName = getTopicName(tenant);
    var messageKey = ctx.getTenantId().toString();

    createEvent(tenant, newPayload, oldPayload).ifPresent(evt -> kafkaEventPublisher.send(topicName, messageKey, evt));
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

  /**
   * Creates {@link ResourceEvent} object for given tenant nane, new and old event bodies.
   *
   * @param tenantName - tenant name as {@link String}
   * @param newPayload - new value in {@link ResourceEvent}
   * @param oldPayload - old value in {@link ResourceEvent}
   * @return {@link Optional} of {@link ResourceEvent}, it will be empty if old and new values are not valid
   */
  private Optional<ResourceEvent<T>> createEvent(String tenantName, T newPayload, T oldPayload) {
    return KafkaEventUtils.createEvent(getResourceName(), tenantName, newPayload, oldPayload);
  }
}
