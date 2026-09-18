package org.folio.entitlement.integration.kafka.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

@UnitTest
class TenantEntitlementKafkaPropertiesTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
    .withInitializer(new ConfigDataApplicationContextInitializer())
    .withUserConfiguration(TestConfiguration.class);

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

  @Test
  void producerTenantCollection_positive_disabledByDefault() {
    contextRunner.run(context -> {
      var properties = context.getBean(TenantEntitlementKafkaProperties.class);
      assertThat(properties.isProducerTenantCollection()).isFalse();
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "false", "FALSE"})
  void producerTenantCollection_positive_disabled(String value) {
    contextRunner.withPropertyValues("KAFKA_PRODUCER_TENANT_COLLECTION=" + value).run(context -> {
      var properties = context.getBean(TenantEntitlementKafkaProperties.class);
      assertThat(properties.isProducerTenantCollection()).isFalse();
    });
  }

  @ParameterizedTest
  @CsvSource({"ALL, ALL", "COLLECTIONA, COLLECTIONA", "true, ALL", "TRUE, ALL"})
  void producerTenantCollection_positive_enabled(String value, String expectedQualifier) {
    contextRunner.withPropertyValues("KAFKA_PRODUCER_TENANT_COLLECTION=" + value).run(context -> {
      var properties = context.getBean(TenantEntitlementKafkaProperties.class);
      assertThat(properties.isProducerTenantCollection()).isTrue();
      assertThat(properties.getTenantCollectionQualifier()).isEqualTo(expectedQualifier);
    });
  }

  @ParameterizedTest
  @ValueSource(strings = {"all", "yes", "COLLECTION-A", "COLLECTION_A", "1ALL", "ABBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB"})
  void producerTenantCollection_negative_invalidValue(String value) {
    contextRunner.withPropertyValues("KAFKA_PRODUCER_TENANT_COLLECTION=" + value).run(context -> {
      assertThat(context).hasFailed();
      assertThat(context.getStartupFailure()).rootCause()
        .hasMessageContaining("must be a tenant collection name matching [A-Z][A-Z0-9]{0,30}");
    });
  }

  private static Properties applicationProperties() {
    var yamlPropertiesFactory = new YamlPropertiesFactoryBean();
    yamlPropertiesFactory.setResources(new ClassPathResource("application.yml"));
    return yamlPropertiesFactory.getObject();
  }

  @EnableConfigurationProperties(TenantEntitlementKafkaProperties.class)
  static class TestConfiguration {}
}
