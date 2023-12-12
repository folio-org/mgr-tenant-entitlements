package org.folio.entitlement.integration.kafka.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaConsumerFactoryCustomizer;
import org.springframework.boot.autoconfigure.kafka.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonSerializer;

@Configuration
public class KafkaConfiguration {

  /**
   * Customizes json serializer for apache kafka.
   *
   * @param objectMapper - {@link ObjectMapper} bean from spring context
   * @return {@link DefaultKafkaConsumerFactoryCustomizer} object
   */
  @Bean
  public DefaultKafkaProducerFactoryCustomizer customizeJsonDeserializer(ObjectMapper objectMapper) {
    return factory -> factory.setValueSerializerSupplier(() -> new JsonSerializer<>(objectMapper));
  }
}
