package org.folio.entitlement.integration.kafka;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class KafkaMessageListener {

  private final ResourceResultEventService resourceResultService;

  @KafkaListener(
    id = "resource-result-event-listener",
    containerFactory = "kafkaListenerContainerFactory",
    groupId = "#{kafkaConsumerProperties.listener['resource-result'].groupId}",
    topicPattern = "#{kafkaConsumerProperties.listener['resource-result'].topicPattern}",
    concurrency = "#{kafkaConsumerProperties.listener['resource-result'].concurrency}")
  public void handleResourceResultEvent(@Payload @Valid ResourceResultEvent event) {
    resourceResultService.processEvent(event);
  }
}
