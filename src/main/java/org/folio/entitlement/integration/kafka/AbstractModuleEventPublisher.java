package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.utils.EntitlementServiceUtils.isModuleUpdated;
import static org.folio.entitlement.utils.EntitlementServiceUtils.isModuleVersionChanged;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.kafka.configuration.TenantEntitlementKafkaProperties;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.service.stage.ModuleDatabaseLoggingStage;
import org.springframework.beans.factory.annotation.Autowired;

@RequiredArgsConstructor
public abstract class AbstractModuleEventPublisher<T> extends ModuleDatabaseLoggingStage {

  protected KafkaEventPublisher kafkaEventPublisher;
  protected TenantEntitlementKafkaProperties tenantEntitlementKafkaProperties;

  @Override
  public void execute(ModuleStageContext ctx) {
    var tenant = ctx.getTenantName();
    var type = ctx.getModuleType();
    var moduleDesc = ctx.getModuleDescriptor();
    var installedModuleDesc = ctx.getInstalledModuleDescriptor();
    var applicationId = ctx.getApplicationId();
    var entitledApplicationId = ctx.getEntitledApplicationId();
    var messageKey = ctx.getTenantId().toString();

    if (!isModuleUpdated(moduleDesc, installedModuleDesc)) {
      if (isModuleVersionChanged(moduleDesc, installedModuleDesc)) {
        getEventPayloadForNotChangedModule(applicationId, entitledApplicationId, type, moduleDesc, installedModuleDesc)
          .flatMap(payload -> createEvent(tenant, payload.getLeft(), payload.getRight()))
          .ifPresent(evt -> kafkaEventPublisher.send(getTopicName(tenant), messageKey, evt));
      }

      return;
    }

    var newPayload = getEventPayload(applicationId, type, moduleDesc).orElse(null);
    var oldPayload = getEventPayload(entitledApplicationId, type, installedModuleDesc).orElse(null);

    var topicName = getTopicName(tenant);
    createEvent(tenant, newPayload, oldPayload).ifPresent(evt -> kafkaEventPublisher.send(topicName, messageKey, evt));
  }

  @Autowired
  public void setKafkaEventPublisher(KafkaEventPublisher kafkaEventPublisher) {
    this.kafkaEventPublisher = kafkaEventPublisher;
  }

  @Autowired
  public void setTenantEntitlementKafkaProperties(TenantEntitlementKafkaProperties tenantEntitlementKafkaProperties) {
    this.tenantEntitlementKafkaProperties = tenantEntitlementKafkaProperties;
  }

  /**
   * Creates event payload from application id and module descriptor.
   *
   * @param applicationId - application identifier as {@link String}
   * @param descriptor - module descriptor as {@link ModuleDescriptor}
   * @return {@link Optional} of created event payload, empty if event payload not provided
   */
  protected abstract Optional<T> getEventPayload(String applicationId, ModuleType type, ModuleDescriptor descriptor);

  /**
   * Creates topic name using tenant name.
   *
   * @param tenantName - tenant name as {@link String}
   * @return kafka topic name
   */
  protected abstract String getTopicNameByTenant(String tenantName);

  /**
   * Creates topic tenants collection name.
   *
   * @return kafka topic tenants collection name
   */
  protected abstract String getTopicNameByTenantCollection();

  /**
   * Returns resource name for {@link ResourceEvent} object.
   *
   * @return resource name
   */
  protected abstract String getResourceName();

  /**
   * Provides a capability send event if module is not changed during upgrade process.
   *
   * @param applicationId - application identifier as {@link String}
   * @param entitledApplicationId - entitled application identifier as {@link String}
   * @param type - module type as {@link ModuleType} enum value
   * @param descriptor - new module descriptor as {@link ModuleDescriptor}
   * @param installedModuleDescriptor - installed module descriptor as {@link ModuleDescriptor}
   * @return {@link Optional} of {@link T} event payload, empty if event payload not provided
   */
  protected Optional<Pair<T, T>> getEventPayloadForNotChangedModule(String applicationId, String entitledApplicationId,
    ModuleType type, ModuleDescriptor descriptor, ModuleDescriptor installedModuleDescriptor) {
    return Optional.empty();
  }

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

  private String getTopicName(String tenantName) {
    return tenantEntitlementKafkaProperties.isProducerTenantCollection()
      ? getTopicNameByTenantCollection() : getTopicNameByTenant(tenantName);
  }
}
