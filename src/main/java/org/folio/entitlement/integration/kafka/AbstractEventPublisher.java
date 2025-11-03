package org.folio.entitlement.integration.kafka;

import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.MODULE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;
import static org.folio.entitlement.utils.EntitlementServiceUtils.isModuleUpdated;
import static org.folio.entitlement.utils.EntitlementServiceUtils.isModuleVersionChanged;

import java.util.Optional;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.ModuleDescriptorHolder;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.integration.okapi.model.OkapiStageContext;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class AbstractEventPublisher<T> extends DatabaseLoggingStage<OkapiStageContext> {

  protected KafkaEventPublisher kafkaEventPublisher;

  @Override
  public void execute(OkapiStageContext context) {
    var tenantName = context.getTenantName();
    var moduleEventsStream = getModuleEventStream(context, MODULE);
    var uiModuleEventsStream = includeUiDescriptors() ? getModuleEventStream(context, UI_MODULE) : Stream.empty();

    var messageKey = context.getTenantId().toString();
    var kafkaTopic = getTopicName(tenantName);
    Stream.concat(moduleEventsStream, uiModuleEventsStream)
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
   * @param type - module type as {@link ModuleType} enum value
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
  protected abstract String getTopicName(String tenantName);

  /**
   * Returns resource name for {@link ResourceEvent} object.
   *
   * @return resource name
   */
  protected abstract String getResourceName();

  /**
   * Returns true if ui module descriptors must be processed in event stream.
   *
   * @return true if ui module descriptors must be processed, false - otherwise
   */
  protected boolean includeUiDescriptors() {
    return false;
  }

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

  private Stream<ResourceEvent<T>> getModuleEventStream(OkapiStageContext ctx, ModuleType moduleType) {
    var moduleEvents = getEventsStream(ctx, moduleType);
    var deprecatedEvents = getDeprecatedEventsStream(ctx, moduleType);
    return Stream.concat(moduleEvents, deprecatedEvents);
  }

  private Stream<ResourceEvent<T>> getEventsStream(OkapiStageContext context, ModuleType moduleType) {
    var tenantName = context.getTenantName();
    var applicationId = context.getApplicationId();
    if (context.getEntitlementType() == UPGRADE) {
      return toStream(context.getModuleDescriptorHolders(moduleType))
        .map(moduleDescriptorHolder -> getEvent(moduleDescriptorHolder, moduleType, context))
        .flatMap(Optional::stream);
    }

    return toStream(context.getModuleDescriptors(moduleType))
      .map(moduleDescriptor -> getEvent(moduleDescriptor, moduleType, tenantName, applicationId, false))
      .flatMap(Optional::stream);
  }

  private Optional<ResourceEvent<T>> getEvent(ModuleDescriptorHolder mdh, ModuleType type, OkapiStageContext ctx) {
    var descriptor = mdh.moduleDescriptor();
    var installedDescriptor = mdh.installedModuleDescriptor();
    var appId = ctx.getApplicationId();
    var entitledAppId = ctx.getEntitledApplicationId();
    var tenantName = ctx.getTenantName();

    if (!isModuleUpdated(descriptor, installedDescriptor)) {
      if (isModuleVersionChanged(descriptor, installedDescriptor)) {
        return getEventPayloadForNotChangedModule(appId, entitledAppId, type, descriptor, installedDescriptor)
          .flatMap(pair -> createEvent(tenantName, pair.getLeft(), pair.getRight()));
      }

      return Optional.empty();
    }

    var newEventPayload = getEventPayload(appId, type, descriptor);
    var oldEventPayload = getEventPayload(entitledAppId, type, mdh.installedModuleDescriptor());
    return createEvent(tenantName, newEventPayload.orElse(null), oldEventPayload.orElse(null));
  }

  private Optional<ResourceEvent<T>> getEvent(ModuleDescriptor moduleDescriptor, ModuleType moduleType,
    String tenantName, String applicationId, boolean isDeprecated) {
    var payload = getEventPayload(applicationId, moduleType, moduleDescriptor).orElse(null);
    return isDeprecated ? createEvent(tenantName, null, payload) : createEvent(tenantName, payload, null);
  }

  private Stream<ResourceEvent<T>> getDeprecatedEventsStream(OkapiStageContext context, ModuleType moduleType) {
    var tenantName = context.getTenantName();
    var entitledApplicationId = context.getEntitledApplicationId();
    return toStream(context.getDeprecatedModuleDescriptors(moduleType))
      .map(moduleDescriptor -> getEvent(moduleDescriptor, moduleType, tenantName, entitledApplicationId, true))
      .flatMap(Optional::stream);
  }

  private Optional<ResourceEvent<T>> createEvent(String tenantName, T newPayload, T oldPayload) {
    return KafkaEventUtils.createEvent(getResourceName(), tenantName, newPayload, oldPayload);
  }
}
