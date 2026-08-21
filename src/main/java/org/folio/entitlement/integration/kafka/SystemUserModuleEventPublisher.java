package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.integration.kafka.KafkaEventUtils.SYSTEM_USER_RESOURCE_NAME;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.SYSTEM_USER_TOPIC;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.TOPIC_TENANT_COLLECTION_KEY;
import static org.folio.integration.kafka.producer.KafkaUtils.getTenantTopicName;

import java.util.Optional;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.integration.kafka.model.SystemUserEvent;
import org.folio.entitlement.utils.SystemUserEventProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Log4j2
@Component
public class SystemUserModuleEventPublisher extends AbstractModuleEventPublisher<SystemUserEvent> {

  private final SystemUserEventProvider systemUserEventProvider;

  public SystemUserModuleEventPublisher(
    @Value("${application.event-publishing.system-user.await-completion:true}") boolean awaitCompletion,
    SystemUserEventProvider systemUserEventProvider) {
    super(awaitCompletion);
    this.systemUserEventProvider = systemUserEventProvider;
  }

  @Override
  protected Optional<SystemUserEvent> getEventPayload(String appId, ModuleType type, ModuleDescriptor descriptor) {
    return systemUserEventProvider.getSystemUserEvent(descriptor);
  }

  @Override
  protected String getTopicNameByTenant(String tenantName) {
    return getTenantTopicName(SYSTEM_USER_TOPIC, tenantName);
  }

  @Override
  protected String getTopicNameByTenantCollection() {
    return getTenantTopicName(SYSTEM_USER_TOPIC, TOPIC_TENANT_COLLECTION_KEY);
  }

  @Override
  protected String getResourceName() {
    return SYSTEM_USER_RESOURCE_NAME;
  }
}
