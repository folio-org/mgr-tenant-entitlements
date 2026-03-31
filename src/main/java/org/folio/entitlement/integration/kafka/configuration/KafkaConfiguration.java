package org.folio.entitlement.integration.kafka.configuration;

import org.springframework.boot.kafka.autoconfigure.DefaultKafkaProducerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class KafkaConfiguration {

  /**
   * Customizes json serializer for apache kafka.
   *
   * @param jsonMapper - {@link JsonMapper} bean from spring context
   * @return {@link DefaultKafkaProducerFactoryCustomizer} object
   */
  @Bean
  public DefaultKafkaProducerFactoryCustomizer customizeJsonDeserializer(JsonMapper jsonMapper) {
    return factory -> factory.setValueSerializerSupplier(() -> new JacksonJsonSerializer<>(jsonMapper));
  }
}
