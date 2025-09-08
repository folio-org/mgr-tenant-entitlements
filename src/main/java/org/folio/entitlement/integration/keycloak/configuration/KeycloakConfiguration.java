package org.folio.entitlement.integration.keycloak.configuration;

import static org.folio.security.integration.keycloak.utils.ClientBuildUtils.buildKeycloakAdminClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCleaner;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCreator;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceUpdater;
import org.folio.entitlement.integration.keycloak.KeycloakModuleResourceCleaner;
import org.folio.entitlement.integration.keycloak.KeycloakModuleResourceCreator;
import org.folio.entitlement.integration.keycloak.KeycloakModuleResourceUpdater;
import org.folio.entitlement.integration.keycloak.KeycloakService;
import org.folio.entitlement.integration.keycloak.configuration.properties.KeycloakConfigurationProperties;
import org.folio.entitlement.retry.keycloak.KeycloakRetrySupportService;
import org.folio.security.integration.keycloak.configuration.properties.KeycloakProperties;
import org.folio.security.integration.keycloak.service.KeycloakModuleDescriptorMapper;
import org.folio.security.integration.keycloak.service.KeycloakStoreKeyProvider;
import org.folio.tools.store.SecureStore;
import org.folio.tools.store.exception.SecretNotFoundException;
import org.keycloak.admin.client.Keycloak;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Log4j2
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(KeycloakProperties.class)
@ConditionalOnProperty("application.keycloak.enabled")
public class KeycloakConfiguration {

  private final KeycloakProperties properties;
  private final KeycloakStoreKeyProvider keycloakStoreKeyProvider;
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
  @ConditionalOnProperty(name = "application.keycloak.enabled", havingValue = "true", matchIfMissing = true)
  public KeycloakService keycloakService(Keycloak client, KeycloakModuleDescriptorMapper mapper,
    KeycloakConfigurationProperties properties, KeycloakRetrySupportService keycloakRetrySupportService) {
    return new KeycloakService(client, mapper, properties, keycloakRetrySupportService);
  }

  @Bean
  @ConditionalOnProperty("application.okapi.enabled")
  public KeycloakAuthResourceCreator keycloakAuthResourceCreator(Keycloak keycloak, KeycloakService keycloakService) {
    return new KeycloakAuthResourceCreator(keycloak, keycloakService);
  }

  @Bean
  @ConditionalOnProperty("application.okapi.enabled")
  public KeycloakAuthResourceUpdater keycloakAuthResourceUpdater(Keycloak keycloak, KeycloakService keycloakService) {
    return new KeycloakAuthResourceUpdater(keycloak, keycloakService);
  }

  @Bean
  @ConditionalOnProperty("application.okapi.enabled")
  public KeycloakAuthResourceCleaner keycloakAuthResourceCleaner(Keycloak keycloak, KeycloakService keycloakService) {
    return new KeycloakAuthResourceCleaner(keycloak, keycloakService);
  }

  @Bean
  @ConditionalOnProperty(value = "application.okapi.enabled", havingValue = "false")
  public KeycloakModuleResourceCreator keycloakModuleResourceCreator(Keycloak keycloak, KeycloakService kcService) {
    return new KeycloakModuleResourceCreator(keycloak, kcService);
  }

  @Bean
  @ConditionalOnProperty(value = "application.okapi.enabled", havingValue = "false")
  public KeycloakModuleResourceUpdater keycloakModuleResourceUpdater(Keycloak keycloak, KeycloakService kcService) {
    return new KeycloakModuleResourceUpdater(keycloak, kcService);
  }

  @Bean
  @ConditionalOnProperty(value = "application.okapi.enabled", havingValue = "false")
  public KeycloakModuleResourceCleaner keycloakModuleResourceCleaner(Keycloak keycloak, KeycloakService kcService) {
    return new KeycloakModuleResourceCleaner(keycloak, kcService);
  }

  private String getKeycloakClientSecret(String clientId) {
    try {
      return secureStore.get(keycloakStoreKeyProvider.globalStoreKey(clientId));
    } catch (SecretNotFoundException e) {
      log.debug("Secret for 'admin' client is not defined in the secret store: clientId = {}", clientId);
      return null;
    }
  }
}
