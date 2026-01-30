package org.folio.entitlement.integration.kafka;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.kafka.configuration.TenantEntitlementKafkaProperties;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Log4j2
@RequiredArgsConstructor
public class KafkaEventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final TenantEntitlementKafkaProperties tenantEntitlementKafkaProperties;

  /**
   * Sends event using provided topic name, message key and message body.
   *
   * @param topic - kafka topic name as {@link String}
   * @param body - event body as {@link Object}
   * @param key - message key as {@link String}
   */
  public void send(String topic, String key, Object body) {
    var sendDurationTimeoutInMillis = tenantEntitlementKafkaProperties.getSendDurationTimeout().toMillis();
    try {
      kafkaTemplate.send(topic, key, body).thenAccept(sendResult ->
        log.info("Message sent to Kafka topic: topic = {}, key = {}, partition = {}, offset = {}. Thread = {}",
          topic, key, sendResult.getRecordMetadata().partition(), sendResult.getRecordMetadata().offset(),
          Thread.currentThread().getName())
      ).get(sendDurationTimeoutInMillis, MILLISECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IntegrationException("Failed to send event", getErrorParameters(topic, key), e);
    } catch (ExecutionException | TimeoutException e) {
      throw new IntegrationException("Failed to send event", getErrorParameters(topic, key), e);
    }
  }

  private static List<Parameter> getErrorParameters(String topic, String key) {
    return List.of(new Parameter().key("topic").value(topic), new Parameter().key("key").value(key));
  }
}
