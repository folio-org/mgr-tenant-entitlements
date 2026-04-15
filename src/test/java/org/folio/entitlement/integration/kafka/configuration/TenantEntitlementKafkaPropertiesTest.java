package org.folio.entitlement.integration.kafka.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

@UnitTest
class TenantEntitlementKafkaPropertiesTest {

  @Test
  void applicationYml_positive_producerTopicsAreSeparatedFromLocalKafkaProperties() {
    var propertyNames = applicationProperties().stringPropertyNames();

    assertThat(propertyNames)
      .contains("application.kafka.producer.topics[0].name")
      .contains("application.kafka.send-duration-timeout")
      .contains("application.kafka.producer-tenant-collection")
      .contains("application.kafka.tenant-topics[0].name")
      .doesNotContain("application.kafka.topics[0].name");
  }

  private static Properties applicationProperties() {
    var yamlPropertiesFactory = new YamlPropertiesFactoryBean();
    yamlPropertiesFactory.setResources(new ClassPathResource("application.yml"));
    return yamlPropertiesFactory.getObject();
  }
}
