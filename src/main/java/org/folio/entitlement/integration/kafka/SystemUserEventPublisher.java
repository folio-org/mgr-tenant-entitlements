package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.integration.kafka.KafkaEventUtils.SYSTEM_USER_RESOURCE_NAME;
import static org.folio.integration.kafka.KafkaUtils.getTenantTopicName;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SystemUserEventPublisher extends AbstractEventPublisher<SystemUserEvent> {

  @Override
  protected Optional<SystemUserEvent> getEventPayload(String appId, ModuleType type, ModuleDescriptor descriptor) {
    return SystemUserModuleEventPublisher.getSystemUserEvent(descriptor);
  }

  @Override
  protected String getTopicName(String tenantName) {
    return getTenantTopicName(KafkaEventUtils.SYSTEM_USER_TOPIC, tenantName);
  }

  @Override
  protected String getResourceName() {
    return SYSTEM_USER_RESOURCE_NAME;
  }
}
