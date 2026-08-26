package org.folio.entitlement.integration.kafka.configuration;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.folio.integration.kafka.consumer.EnableKafkaConsumer;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.integration.kafka.producer.EnableKafkaProducer;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.boot.kafka.autoconfigure.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableKafkaProducer
@EnableKafkaConsumer
@RequiredArgsConstructor
public class KafkaConfiguration implements KafkaListenerConfigurer {

  /**
   * Suffix of the topic that unprocessable {@code resource-result} records are republished to.
   */
  public static final String DLT_SUFFIX = ".dlt";

  private static final long RETRY_INTERVAL_MS = 1_000L;
  private static final long MAX_RETRIES = 3L;

  private final LocalValidatorFactoryBean validator;

  /**
   * Customizes json serializer for apache kafka.
   *
   * @param jsonMapper - {@link JsonMapper} bean from spring context
   * @return {@link DefaultKafkaProducerFactoryCustomizer} object
   */
  @Bean
  public DefaultKafkaProducerFactoryCustomizer customizeJsonSerializer(JsonMapper jsonMapper) {
    return factory -> factory.setValueSerializerSupplier(() -> new JacksonJsonSerializer<>(jsonMapper));
  }

  /**
   * Container factory dedicated to the {@code resource-result} listener.
   *
   * <p>Scoped to a single listener on purpose. The target type has to be pinned to {@link ResourceResultEvent}
   * because inbound producers may omit the {@code spring.json.type.id} header, but pinning it on the shared
   * consumer factory would force every future listener in this module to deserialize into the same type. A
   * dedicated factory keeps that constraint where it belongs.</p>
   *
   * <p>Two safety nets are attached here. {@link ErrorHandlingDeserializer} converts a malformed payload into a
   * handled record rather than a deserialization failure the container cannot seek past. The
   * {@link DeadLetterPublishingRecoverer} then retains anything unprocessable instead of committing it away with
   * only a log entry - for this feature a discarded result is not a lost log line, it is the one signal that would
   * have moved a stage out of {@code IN_PROGRESS}. Structurally invalid records skip the retries, because no
   * number of attempts makes a malformed id valid.</p>
   *
   * @param kafkaProperties - Spring Boot Kafka properties
   * @param jsonMapper - {@link JsonMapper} bean from spring context
   * @param kafkaTemplate - template used to republish to the dead-letter topic
   * @return container factory for the {@code resource-result} listener
   */
  @Bean
  public ConcurrentKafkaListenerContainerFactory<String, ResourceResultEvent> resourceResultContainerFactory(
    KafkaProperties kafkaProperties, JsonMapper jsonMapper, KafkaTemplate<String, Object> kafkaTemplate) {

    var valueDeserializer = new ErrorHandlingDeserializer<>(
      new JacksonJsonDeserializer<>(ResourceResultEvent.class, jsonMapper));

    var consumerFactory = new DefaultKafkaConsumerFactory<>(
      kafkaProperties.buildConsumerProperties(), new StringDeserializer(), valueDeserializer);

    var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
      (record, exception) -> new TopicPartition(record.topic() + DLT_SUFFIX, record.partition()));

    var errorHandler = new DefaultErrorHandler(recoverer, new FixedBackOff(RETRY_INTERVAL_MS, MAX_RETRIES));
    errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);

    var factory = new ConcurrentKafkaListenerContainerFactory<String, ResourceResultEvent>();
    factory.setConsumerFactory(consumerFactory);
    factory.setCommonErrorHandler(errorHandler);
    return factory;
  }

  @Override
  public void configureKafkaListeners(KafkaListenerEndpointRegistrar registrar) {
    registrar.setValidator(this.validator);
  }
}
