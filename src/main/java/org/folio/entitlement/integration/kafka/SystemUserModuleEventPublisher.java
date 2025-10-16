package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.integration.kafka.KafkaEventUtils.SYSTEM_USER_RESOURCE_NAME;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.SYSTEM_USER_TOPIC;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.TOPIC_TENANT_COLLECTION_KEY;

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
import org.folio.entitlement.utils.SystemUserEventProvider;
import org.folio.integration.kafka.KafkaUtils;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class SystemUserModuleEventPublisher extends AbstractModuleEventPublisher<SystemUserEvent> {

  private final SystemUserEventProvider systemUserEventProvider;

  @Override
  protected Optional<SystemUserEvent> getEventPayload(String appId, ModuleType type, ModuleDescriptor descriptor) {
    return systemUserEventProvider.getSystemUserEvent(descriptor);
  }

  @Override
  protected String getTopicNameByTenant(String tenantName) {
    return KafkaUtils.getTenantTopicName(SYSTEM_USER_TOPIC, tenantName);
  }

  @Override
  protected String getTopicNameByTenantCollection() {
    return KafkaUtils.getTenantTopicName(SYSTEM_USER_TOPIC, TOPIC_TENANT_COLLECTION_KEY);
  }

  @Override
  protected String getResourceName() {
    return SYSTEM_USER_RESOURCE_NAME;
  }
}
