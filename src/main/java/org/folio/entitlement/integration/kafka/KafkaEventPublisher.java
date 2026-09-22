package org.folio.entitlement.integration.kafka;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.integration.kafka.configuration.TenantEntitlementKafkaProperties;
import org.folio.integration.kafka.consumer.KafkaTenantHeaders;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class KafkaEventPublisher {

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final TenantEntitlementKafkaProperties tenantEntitlementKafkaProperties;

  /**
   * Sends event using provided topic name, message key, tenant, and message body.
   *
   * @param topic - kafka topic name as {@link String}
   * @param key - message key as {@link String}
   * @param tenant - tenant name as {@link String}
   * @param body - event body as {@link Object}
   */
  public void send(String topic, String key, String tenant, Object body) {
    var sendDurationTimeoutInMillis = tenantEntitlementKafkaProperties.getSendDurationTimeout().toMillis();
    var producerRecord = new ProducerRecord<>(topic, null, key, body, KafkaTenantHeaders.tenantHeaders(tenant));
    try {
      kafkaTemplate.send(producerRecord).get(sendDurationTimeoutInMillis, MILLISECONDS);
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
