package org.folio.entitlement.service.validator.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "application.validation.interface-integrity")
public class InterfaceIntegrityValidatorProperties {
  private boolean enabled;
}
