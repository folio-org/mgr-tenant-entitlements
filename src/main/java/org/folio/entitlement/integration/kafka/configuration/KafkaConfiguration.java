package org.folio.entitlement.integration.kafka.configuration;

import lombok.RequiredArgsConstructor;
import org.folio.integration.kafka.consumer.EnableKafkaConsumer;
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

  @Bean
  public DefaultKafkaConsumerFactoryCustomizer customizeJsonDeserializer(JsonMapper jsonMapper) {
    return factory -> factory.setValueDeserializer(new JacksonJsonDeserializer<>(jsonMapper));
  }

  @Override
  public void configureKafkaListeners(KafkaListenerEndpointRegistrar registrar) {
    registrar.setValidator(this.validator);
  }
}
