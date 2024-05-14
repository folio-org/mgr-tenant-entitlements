package org.folio.entitlement.integration.tm;

import lombok.Data;
import org.folio.common.configuration.properties.TlsProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "application.mt")
public class TenantManagerProperties {

  private String url;

  /**
   * Properties object with an information about TLS connection.
   */
  private TlsProperties tls;
}
