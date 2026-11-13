package org.folio.entitlement.integration.folio.stage;

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
import org.folio.entitlement.integration.folio.FolioModuleService;
import org.folio.entitlement.repository.FlowStageRepository;
import org.folio.entitlement.retry.RetryAttemptListener;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.folio.flow.impl.StageContextImpl;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
@ContextConfiguration(classes = FolioModuleCallsRetriesTest.TestConfig.class)
class FolioModuleCallsRetriesTest {

  @Autowired private FolioModuleService folioModuleService;

  @Autowired private FolioModuleInstaller folioModuleInstaller;

  @Autowired private FolioModuleUninstaller folioModuleUninstaller;

  @Autowired private FolioModuleUpdater folioModuleUpdater;

  @AfterEach
  void resetMocks() {
    reset(folioModuleService);
  }

  @Test
  void testRetryOnInstallerExecute() {
    doThrow(new IntegrationException("Test", List.of(), 400)).when(folioModuleService).enable(any());
    var moduleStageContext = createModuleStageContext();
    assertThatThrownBy(() -> folioModuleInstaller.execute(moduleStageContext)).isInstanceOf(IntegrationException.class);
    verify(folioModuleService, times(3)).enable(any());
  }

  @Test
  void testRetryOnInstallerCancel() {
    doThrow(new IntegrationException("Test", List.of(), 400)).when(folioModuleService).disable(any());
    var moduleStageContext = createModuleStageContext();
    assertThatThrownBy(() -> folioModuleInstaller.cancel(moduleStageContext)).isInstanceOf(IntegrationException.class);
    verify(folioModuleService, times(3)).disable(any());
  }

  @Test
  void testRetryOnUpdaterExecute() {
    doThrow(new IntegrationException("Test", List.of(), 400)).when(folioModuleService).enable(any());
    var moduleStageContext = createModuleStageContext();
    assertThatThrownBy(() -> folioModuleUpdater.execute(moduleStageContext)).isInstanceOf(IntegrationException.class);
    verify(folioModuleService, times(3)).enable(any());
  }

  @Test
  void testRetryOnUninstallerExecute() {
    doThrow(new IntegrationException("Test", List.of(), 400)).when(folioModuleService).disable(any());
    var moduleStageContext = createModuleStageContext();
    assertThatThrownBy(() -> folioModuleUninstaller.execute(moduleStageContext)).isInstanceOf(
      IntegrationException.class);
    verify(folioModuleService, times(3)).disable(any());
  }

  @Test
  void execute_negative_nonRetryableErrorIsNotRetried() {
    var error = new IntegrationException("Test", List.of());
    doThrow(error).when(folioModuleService).enable(any());
    var moduleStageContext = createModuleStageContext();

    assertThatThrownBy(() -> folioModuleInstaller.execute(moduleStageContext)).isSameAs(error);

    verify(folioModuleService, times(1)).enable(any());
  }

  @Test
  void execute_negative_failedAttemptsAreRecordedInStageContext() {
    var error = new IntegrationException("Test", List.of(), 400);
    doThrow(error).when(folioModuleService).enable(any());
    var moduleStageContext = createModuleStageContext();

    assertThatThrownBy(() -> folioModuleInstaller.execute(moduleStageContext)).isSameAs(error);

    var retryInformation = (RetryInformation) moduleStageContext.get(ATTR_RETRY_INFO);
    assertThat(retryInformation.getRetriesCount()).isEqualTo(3);
    assertThat(retryInformation.getErrors()).hasSize(3);
  }

  @Test
  void execute_negative_keycloakErrorIsNotRetriedByFolioModuleAdvisor() {
    var error = new WebApplicationException("Test", 500);
    doThrow(error).when(folioModuleService).enable(any());
    var moduleStageContext = createModuleStageContext();

    assertThatThrownBy(() -> folioModuleInstaller.execute(moduleStageContext)).isSameAs(error);

    verify(folioModuleService, times(1)).enable(any());
  }

  @Test
  void execute_negative_interruptionDuringBackoffStopsRetries() {
    var error = new IntegrationException("Test", List.of(), 400);
    doThrow(error).when(folioModuleService).enable(any());
    var moduleStageContext = createModuleStageContext();

    Thread.currentThread().interrupt();
    try {
      assertThatThrownBy(() -> folioModuleInstaller.execute(moduleStageContext)).isSameAs(error);
      assertThat(Thread.currentThread().isInterrupted()).isTrue();
    } finally {
      Thread.interrupted();
    }

    verify(folioModuleService, times(1)).enable(any());
  }

  private static ModuleStageContext createModuleStageContext() {
    return new ModuleStageContext(new StageContextImpl("test",
      Map.of(PARAM_REQUEST, EntitlementRequest.builder().build(), PARAM_MODULE_DESCRIPTOR, new ModuleDescriptor()),
      Map.of()));
  }

  @Configuration
  public static class TestConfig {

    private RetryConfigProps quickBackoff() {
      return RetryConfigProps.builder()
        .backoff(RetryBackoffConfigProps.builder().delay(1).maxdelay(1).multiplier(1).build()).build();
    }

    @Bean
    public RetryConfigurationProperties retryConfigurationProperties() {
      return RetryConfigurationProperties.builder().module(quickBackoff()).build();
    }

    @Bean
    public FolioModuleService folioModuleService() {
      return mock(FolioModuleService.class);
    }

    @Bean
    public FlowStageRepository flowStageRepository() {
      return mock(FlowStageRepository.class);
    }

    @Bean
    public EntitlementModuleService moduleService() {
      return mock(EntitlementModuleService.class);
    }

    @Bean
    @Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
    public FolioModuleInstaller folioModuleInstaller(FolioModuleService folioModuleService) {
      return new FolioModuleInstaller(folioModuleService);
    }

    @Bean
    @Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
    public FolioModuleUninstaller folioModuleUninstaller(FolioModuleService folioModuleService) {
      return new FolioModuleUninstaller(folioModuleService);
    }

    @Bean
    @Scope(proxyMode = ScopedProxyMode.TARGET_CLASS)
    public FolioModuleUpdater folioModuleUpdater(FolioModuleService folioModuleService,
      EntitlementModuleService moduleService) {
      return new FolioModuleUpdater(folioModuleService, moduleService);
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
