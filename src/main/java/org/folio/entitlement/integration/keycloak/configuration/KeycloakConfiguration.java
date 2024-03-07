package org.folio.entitlement.integration.keycloak.configuration;

import static org.folio.security.integration.keycloak.utils.ClientBuildUtils.buildKeycloakAdminClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCleaner;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCreator;
import org.folio.entitlement.integration.keycloak.KeycloakService;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties;
import org.folio.security.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.security.integration.keycloak.service.KeycloakModuleDescriptorMapper;
import org.folio.security.integration.keycloak.utils.KeycloakSecretUtils;
import org.folio.tools.store.SecureStore;
import org.folio.tools.store.exception.NotFoundException;
import org.keycloak.admin.client.Keycloak;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(KeycloakProperties.class)
@ConditionalOnBean(KeycloakConfigurationProperties.class)
public class KeycloakConfiguration {

  private final KeycloakProperties properties;
  private final SecureStore secureStore;

  @Bean
  @ConditionalOnProperty(name = "application.keycloak.import.enabled", havingValue = "false", matchIfMissing = true)
  public Keycloak keycloak() {
    var clientId = properties.getAdmin().getClientId();
    var clientSecret = getKeycloakClientSecret(clientId);
    return buildKeycloakAdminClient(clientSecret, properties);
  }

  @Bean
  @ConditionalOnProperty(name = "application.keycloak.import.enabled", havingValue = "false", matchIfMissing = true)
  public KeycloakModuleDescriptorMapper moduleDescriptorMapper() {
    return new KeycloakModuleDescriptorMapper();
  }

  @Bean
  public KeycloakService keycloakService(Keycloak client, KeycloakModuleDescriptorMapper mapper,
    KeycloakConfigurationProperties properties) {
    return new KeycloakService(client, mapper, properties);
  }

  @Bean
  public KeycloakAuthResourceCreator keycloakAuthResourceCreator(Keycloak keycloak, KeycloakService keycloakService) {
    return new KeycloakAuthResourceCreator(keycloak, keycloakService);
  }

  @Bean
  public KeycloakAuthResourceCleaner keycloakAuthResourceCleaner(Keycloak keycloak, KeycloakService keycloakService) {
    return new KeycloakAuthResourceCleaner(keycloak, keycloakService);
  }

  private String getKeycloakClientSecret(String clientId) {
    try {
      return secureStore.get(KeycloakSecretUtils.globalStoreKey(clientId));
    } catch (NotFoundException e) {
      log.debug("Secret for 'admin' client is not defined in the secret store: clientId = {}", clientId);
      return null;
    }
  }
}
