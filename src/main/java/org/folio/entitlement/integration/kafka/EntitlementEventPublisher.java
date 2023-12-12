package org.folio.entitlement.integration.kafka;

import static org.folio.integration.kafka.KafkaUtils.getEnvTopicName;

import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntitlementEventPublisher {

  private final KafkaEventPublisher kafkaEventPublisher;

  /**
   * Sends module entitlement event to 'entitlement' tenant-specific topic.
   *
   * @param event - {@link EntitlementEvent} event body
   */
  public void publish(EntitlementEvent event) {
    var entitlementTopic = getEnvTopicName("entitlement");
    kafkaEventPublisher.send(entitlementTopic, event.getModuleId(), event);
  }
}
