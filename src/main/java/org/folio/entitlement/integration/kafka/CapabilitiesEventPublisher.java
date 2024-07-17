package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.integration.kafka.CapabilitiesModuleEventPublisher.getCapabilityEventPayload;
import static org.folio.entitlement.integration.kafka.CapabilitiesModuleEventPublisher.getEventPayloadForUnchangedModule;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.CAPABILITIES_TOPIC;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.CAPABILITY_RESOURCE_NAME;
import static org.folio.integration.kafka.KafkaUtils.getTenantTopicName;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.kafka.model.CapabilityEventPayload;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class CapabilitiesEventPublisher extends AbstractEventPublisher<CapabilityEventPayload> {

  @Override
  protected Optional<CapabilityEventPayload> getEventPayload(String appId, ModuleType type, ModuleDescriptor desc) {
    return getCapabilityEventPayload(appId, type, desc);
  }

  @Override
  protected Optional<Pair<CapabilityEventPayload, CapabilityEventPayload>> getEventPayloadForNotChangedModule(
    String appId, String entitledAppId, ModuleType type, ModuleDescriptor desc, ModuleDescriptor installedDesc) {
    return getEventPayloadForUnchangedModule(appId, entitledAppId, type, desc, installedDesc);
  }

  @Override
  protected String getTopicName(String tenantName) {
    return getTenantTopicName(CAPABILITIES_TOPIC, tenantName);
  }

  @Override
  protected String getResourceName() {
    return CAPABILITY_RESOURCE_NAME;
  }

  @Override
  protected boolean includeUiDescriptors() {
    return true;
  }
}
