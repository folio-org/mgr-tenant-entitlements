package org.folio.entitlement.integration.kafka.configuration;

import java.time.Duration;
import java.util.List;
import lombok.Data;
import org.folio.integration.kafka.FolioKafkaProperties.KafkaTopic;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("application.kafka")
public class TenantEntitlementKafkaProperties {

  private Duration sendDurationTimeout;

  @NestedConfigurationProperty
  private List<KafkaTopic> tenantTopics;
}
