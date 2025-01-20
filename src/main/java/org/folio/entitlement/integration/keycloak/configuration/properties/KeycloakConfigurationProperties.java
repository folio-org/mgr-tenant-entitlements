package org.folio.entitlement.integration.keycloak.configuration.properties;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConditionalOnProperty("application.keycloak.enabled")
@ConfigurationProperties(prefix = "application.keycloak")
public class KeycloakConfigurationProperties {

  /**
   * Defines if tenant entitlement manager is integrated with Keycloak.
   */
  private boolean enabled;

  /**
   * Base url fo Keycloak.
   */
  private String url;

  /**
   * Properties object with an information about login client in Keycloak.
   */
  private Login login;

  /**
   * Authorization token cache max size.
   */
  private long authorizationCacheMaxSize;

  /**
   * Authorization token cache ttl offset.
   */
  private long authorizationCacheTtlOffset;

  @Data
  public static class Login {

    private String clientNameSuffix;
  }
}
