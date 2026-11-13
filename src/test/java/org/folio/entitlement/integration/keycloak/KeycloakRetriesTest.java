package org.folio.entitlement.integration.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.ModuleStageContext.ATTR_RETRY_INFO;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_DESCRIPTOR;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.WebApplicationException;
import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.configuration.RetryConfiguration;
import org.folio.entitlement.configuration.RetryConfigurationProperties;
import org.folio.entitlement.configuration.RetryConfigurationProperties.RetryBackoffConfigProps;
import org.folio.entitlement.configuration.RetryConfigurationProperties.RetryConfigProps;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.ModuleStageContext;
import org.folio.entitlement.domain.model.RetryInformation;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.repository.FlowStageRepository;
import org.folio.entitlement.retry.RetryAttemptListener;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.folio.flow.impl.StageContextImpl;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.token.TokenManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.test.context.ContextConfiguration;

@UnitTest
@SpringBootTest
@Import(RetryConfiguration.class)
@ContextConfiguration(classes = KeycloakRetriesTest.TestConfig.class)
class KeycloakRetriesTest {

  @Autowired private KeycloakService keycloakService;

  @Autowired private KeycloakModuleResourceCreator keycloakModuleResourceCreator;
  @Autowired private KeycloakModuleResourceUpdater keycloakModuleResourceUpdater;
  @Autowired private KeycloakModuleResourceCleaner keycloakModuleResourceCleaner;

  @AfterEach
  void resetMocks() {
    reset(keycloakService);
  }

  @Test
  void testRetryOnModuleResourceCreate() {
    doThrow(new WebApplicationException("test", 500)).when(keycloakService).updateAuthResources(any(), any(), any());
    var context = createModuleStageContext();
    assertThatThrownBy(() -> keycloakModuleResourceCreator.execute(context)).isInstanceOf(
      WebApplicationException.class);
    verify(keycloakService, times(3)).updateAuthResources(any(), any(), any());
  }

  @Test
  void testRetryOnModuleResourceUpdate() {
    doThrow(new WebApplicationException("test", 500)).when(keycloakService).updateAuthResources(any(), any(), any());
    var context = createModuleStageContext();
    assertThatThrownBy(() -> keycloakModuleResourceUpdater.execute(context)).isInstanceOf(
      WebApplicationException.class);
    verify(keycloakService, times(3)).updateAuthResources(any(), any(), any());
  }

  @Test
  void testRetryOnModuleResourceCleanup() {
    doThrow(new WebApplicationException("test", 500)).when(keycloakService).removeAuthResources(any(), any());
    var context = createModuleStageContext();
    assertThatThrownBy(() -> keycloakModuleResourceCleaner.execute(context)).isInstanceOf(
      WebApplicationException.class);
    verify(keycloakService, times(3)).removeAuthResources(any(), any());
  }

  @Test
  void execute_negative_failedAttemptsAreRecordedInStageContext() {
    var error = new WebApplicationException("test", 500);
    doThrow(error).when(keycloakService).updateAuthResources(any(), any(), any());
    var context = createModuleStageContext();

    assertThatThrownBy(() -> keycloakModuleResourceCreator.execute(context)).isSameAs(error);

    var retryInformation = (RetryInformation) context.get(ATTR_RETRY_INFO);
    assertThat(retryInformation.getRetriesCount()).isEqualTo(3);
    assertThat(retryInformation.getErrors()).hasSize(3);
  }

  @Test
  void execute_negative_folioModuleErrorIsNotRetriedByKeycloakAdvisor() {
    var error = new IntegrationException("test", List.of(), 500);
    doThrow(error).when(keycloakService).updateAuthResources(any(), any(), any());
    var context = createModuleStageContext();

    assertThatThrownBy(() -> keycloakModuleResourceCreator.execute(context)).isSameAs(error);

    verify(keycloakService, times(1)).updateAuthResources(any(), any(), any());
  }

  private static ModuleStageContext createModuleStageContext() {
    return new ModuleStageContext(new StageContextImpl("test",
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
      var keycloak = mock(Keycloak.class);
      when(keycloak.tokenManager()).thenReturn(mock(TokenManager.class));
      return keycloak;
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
    public RetryAttemptListener retryAttemptListener(ThreadLocalModuleStageContext threadLocalModuleStageContext) {
      return new RetryAttemptListener(threadLocalModuleStageContext);
    }

    @Bean
    public ThreadLocalModuleStageContext threadLocalModuleStageContext() {
      return new ThreadLocalModuleStageContext();
    }
  }
}
