package org.folio.entitlement.integration.kafka.configuration;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.common.serialization.Deserializer;
import org.folio.integration.kafka.consumer.EnableKafkaConsumer;
import org.folio.integration.kafka.model.ResourceResultEvent;
import org.folio.integration.kafka.producer.EnableKafkaProducer;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableKafkaProducer
@EnableKafkaConsumer
@RequiredArgsConstructor
public class KafkaConfiguration implements KafkaListenerConfigurer {

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
   * Customizes json deserializer for apache kafka.
   *
   * <p>The target type is pinned to {@link ResourceResultEvent} because the application has a
   * single {@code @KafkaListener} that consumes only that event type.  Pinning avoids reliance on
   * {@code spring.json.type.id} headers, which incoming producers may omit.
   *
   * @param jsonMapper - {@link JsonMapper} bean from spring context
   * @return {@link DefaultKafkaConsumerFactoryCustomizer} object
   */
  @Bean
  @SuppressWarnings("unchecked")
  public DefaultKafkaConsumerFactoryCustomizer customizeJsonDeserializer(JsonMapper jsonMapper) {
    // Raw cast required: DefaultKafkaConsumerFactoryCustomizer uses a wildcard-typed factory
    // (DefaultKafkaConsumerFactory<?, ?>), so setValueDeserializer(Deserializer<V>) is
    // effectively Deserializer<?>, which is incompatible with a concrete Deserializer<T>
    // without an unchecked cast.
    return factory -> factory.setValueDeserializer(
      (Deserializer) new JacksonJsonDeserializer<>(ResourceResultEvent.class, jsonMapper));
  }

  @Override
  public void configureKafkaListeners(KafkaListenerEndpointRegistrar registrar) {
    registrar.setValidator(this.validator);
  }
}
