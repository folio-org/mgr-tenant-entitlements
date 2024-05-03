package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.integration.kafka.KafkaEventUtils.SCHEDULED_JOB_RESOURCE_NAME;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.SCHEDULED_JOB_TOPIC;
import static org.folio.entitlement.integration.kafka.ScheduledJobModuleEventPublisher.getScheduledTimers;
import static org.folio.integration.kafka.KafkaUtils.getTenantTopicName;

import java.util.Optional;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.integration.kafka.model.ScheduledTimers;
import org.springframework.stereotype.Component;

@Component
public class ScheduledJobEventPublisher extends AbstractEventPublisher<ScheduledTimers> {

  @Override
  protected Optional<ScheduledTimers> getEventPayload(String appId, ModuleType type, ModuleDescriptor descriptor) {
    return getScheduledTimers(appId, descriptor);
  }

  @Override
  protected String getTopicName(String tenantName) {
    return getTenantTopicName(SCHEDULED_JOB_TOPIC, tenantName);
  }

  @Override
  protected String getResourceName() {
    return SCHEDULED_JOB_RESOURCE_NAME;
  }
}
