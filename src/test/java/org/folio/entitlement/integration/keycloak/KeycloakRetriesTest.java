package org.folio.entitlement.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_DESCRIPTOR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import jakarta.ws.rs.WebApplicationException;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.configuration.RetryConfiguration;
import org.folio.entitlement.configuration.RetryConfigurationProperties;
import org.folio.entitlement.configuration.RetryConfigurationProperties.RetryBackoffConfigProps;
import org.folio.entitlement.configuration.RetryConfigurationProperties.RetryConfigProps;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.integration.okapi.model.OkapiStageContext;
import org.folio.entitlement.repository.FlowStageRepository;
import org.folio.flow.impl.StageContextImpl;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@EnableRetry
@Import(RetryConfiguration.class)
@ContextConfiguration(classes = KeycloakRetriesTest.TestConfig.class)
class KeycloakRetriesTest {

  @Autowired private KeycloakService keycloakService;

  @Autowired private KeycloakModuleResourceCreator keycloakModuleResourceCreator;
  @Autowired private KeycloakModuleResourceUpdater keycloakModuleResourceUpdater;
  @Autowired private KeycloakModuleResourceCleaner keycloakModuleResourceCleaner;

  @Autowired private KeycloakAuthResourceCreator keycloakAuthResourceCreator;
  @Autowired private KeycloakAuthResourceUpdater keycloakAuthResourceUpdater;
  @Autowired private KeycloakAuthResourceCleaner keycloakAuthResourceCleaner;

  @Test
  void testRetryOnModuleResourceCreate() {
    doThrow(new WebApplicationException("test", 500)).when(keycloakService).updateAuthResources(any(), any(), any());
    assertThatThrownBy(() -> keycloakModuleResourceCreator.execute(createModuleStageContext())).isInstanceOf(
      WebApplicationException.class);
  }

  @Test
  void testRetryOnModuleResourceUpdate() {
    doThrow(new WebApplicationException("test", 500)).when(keycloakService).updateAuthResources(any(), any(), any());
    assertThatThrownBy(() -> keycloakModuleResourceUpdater.execute(createModuleStageContext())).isInstanceOf(
      WebApplicationException.class);
  }

  @Test
  void testRetryOnModuleResourceCleanup() {
    doThrow(new WebApplicationException("test", 500)).when(keycloakService).removeAuthResources(any(), any());
    assertThatThrownBy(() -> keycloakModuleResourceCleaner.execute(createModuleStageContext())).isInstanceOf(
      WebApplicationException.class);
  }

  @Test
  void testRetryOnAuthResourceCreate() {
    doThrow(new WebApplicationException("test", 500)).when(keycloakService).updateAuthResources(any(), any(), any());
    assertThatThrownBy(() -> keycloakAuthResourceCreator.execute(createOkapiStageContext())).isInstanceOf(
      WebApplicationException.class);
  }

  @Test
  void testRetryOnAuthResourceUpdate() {
    doThrow(new WebApplicationException("test", 500)).when(keycloakService).updateAuthResources(any(), any(), any());
    assertThatThrownBy(() -> keycloakAuthResourceUpdater.execute(createOkapiStageContext())).isInstanceOf(
      WebApplicationException.class);
  }

  @Test
  void testRetryOnAuthResourceCleanup() {
    doThrow(new WebApplicationException("test", 500)).when(keycloakService).removeAuthResources(any(), any());
    assertThatThrownBy(() -> keycloakAuthResourceCleaner.execute(createOkapiStageContext())).isInstanceOf(
      WebApplicationException.class);
  }

  private static ModuleStageContext createModuleStageContext() {
    return new ModuleStageContext(new StageContextImpl("test",
      Map.of(PARAM_REQUEST, EntitlementRequest.builder().purge(true).build(), PARAM_MODULE_DESCRIPTOR,
        new ModuleDescriptor()), Map.of()));
  }

  private static OkapiStageContext createOkapiStageContext() {
    return new OkapiStageContext(new StageContextImpl("test",
      Map.of(PARAM_REQUEST, EntitlementRequest.builder().purge(true).build(), PARAM_MODULE_DESCRIPTOR,
        new ModuleDescriptor()), Map.of()));
  }

  @Configuration
  public static class TestConfig {

    private RetryConfigProps quickBackoff() {
      return RetryConfigProps.builder()
        .backoff(RetryBackoffConfigProps.builder().delay(1).maxdelay(1).multiplier(1).build()).build();
    }

    @Bean
    public RetryConfigurationProperties retryConfigurationProperties() {
      return RetryConfigurationProperties.builder().keycloak(quickBackoff()).build();
    }

    @Bean
    public FlowStageRepository flowStageRepository() {
      return mock(FlowStageRepository.class);
    }

    @Bean
    public Keycloak keycloak() {
      return mock(Keycloak.class);
    }

    @Bean
    public KeycloakService keycloakService() {
      return mock(KeycloakService.class);
    }

    @Bean
    @Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
    public KeycloakModuleResourceCreator keycloakModuleResourceCreator(Keycloak keycloak,
      KeycloakService keycloakService) {
      return new KeycloakModuleResourceCreator(keycloak, keycloakService);
    }

    @Bean
    @Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
    public KeycloakModuleResourceUpdater keycloakModuleResourceUpdater(Keycloak keycloak,
      KeycloakService keycloakService) {
      return new KeycloakModuleResourceUpdater(keycloak, keycloakService);
    }

    @Bean
    @Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
    public KeycloakModuleResourceCleaner keycloakModuleResourceCleaner(Keycloak keycloak,
      KeycloakService keycloakService) {
      return new KeycloakModuleResourceCleaner(keycloak, keycloakService);
    }

    @Bean
    @Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
    public KeycloakAuthResourceCreator keycloakAuthResourceCreator(Keycloak keycloak, KeycloakService keycloakService) {
      return new KeycloakAuthResourceCreator(keycloak, keycloakService);
    }

    @Bean
    @Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
    public KeycloakAuthResourceUpdater keycloakAuthResourceUpdater(Keycloak keycloak, KeycloakService keycloakService) {
      return new KeycloakAuthResourceUpdater(keycloak, keycloakService);
    }

    @Bean
    @Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
    public KeycloakAuthResourceCleaner keycloakAuthResourceCleaner(Keycloak keycloak, KeycloakService keycloakService) {
      return new KeycloakAuthResourceCleaner(keycloak, keycloakService);
    }
  }
}
