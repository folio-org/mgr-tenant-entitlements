package org.folio.entitlement.integration.kafka;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class KafkaMessageListener {

  @KafkaListener(
    id = "resource-result-event-listener",
    containerFactory = "kafkaListenerContainerFactory",
    groupId = "#{kafkaConsumerProperties.listener['resource-result'].groupId}",
    topicPattern = "#{kafkaConsumerProperties.listener['resource-result'].topicPattern}",
    concurrency = "#{kafkaConsumerProperties.listener['resource-result'].concurrency}")
  public void handleStageResultEvent(@Payload @Valid ResourceResultEvent resultEvent) {
    
  }
}
