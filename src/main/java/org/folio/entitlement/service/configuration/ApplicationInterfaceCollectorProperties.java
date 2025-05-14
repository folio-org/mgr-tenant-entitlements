package org.folio.entitlement.service.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "application.validation.interface-integrity.interface-collector")
public class ApplicationInterfaceCollectorProperties {

  @NestedConfigurationProperty
  private CollectedInterfaceSettings required = new CollectedInterfaceSettings();
}
