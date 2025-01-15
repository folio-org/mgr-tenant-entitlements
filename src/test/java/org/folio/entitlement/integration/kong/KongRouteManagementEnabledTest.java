package org.folio.entitlement.integration.kong;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import feign.Contract;
import feign.codec.Decoder;
import feign.codec.Encoder;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.folio.entitlement.configuration.RetryConfigurationProperties;
import org.folio.entitlement.integration.folio.flow.FolioModuleEntitleFlowFactory;
import org.folio.entitlement.integration.folio.stage.FolioModuleEventPublisher;
import org.folio.entitlement.integration.folio.stage.FolioModuleInstaller;
import org.folio.entitlement.integration.kafka.CapabilitiesModuleEventPublisher;
import org.folio.entitlement.integration.kafka.ScheduledJobModuleEventPublisher;
import org.folio.entitlement.integration.kafka.SystemUserModuleEventPublisher;
import org.folio.entitlement.integration.keycloak.KeycloakModuleResourceCreator;
import org.folio.entitlement.repository.FlowStageRepository;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.folio.flow.model.FlowExecutionStrategy;
import org.folio.tools.kong.client.KongAdminClient;
import org.folio.tools.kong.configuration.KongConfigurationProperties;
import org.folio.tools.kong.service.KongGatewayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {"routemanagement.enable=true"})
@ContextConfiguration(classes = {KongRouteManagementEnabledTest.KongConfigExt.class,
  KongRouteManagementEnabledTest.TestConfig.class})
public class KongRouteManagementEnabledTest {

  @Autowired private FolioModuleEntitleFlowFactory flowFactory;

  @Test
  public void testFlowFactory() {
    var flow = flowFactory.createModuleFlow("123", FlowExecutionStrategy.IGNORE_ON_ERROR, Map.of());
    // Check that we have a ResourceCreatorParallelStage resource creator stage that does both Kong routes
    // and Keycloak resources creation, but not a separate KeycloakModuleResourceCreator stage
    assertThat(flow.getStages().stream().filter(stage -> "ResourceCreatorParallelStage".equals(stage.getStageId()))
      .findAny()).isPresent();
    assertThat(flow.getStages().stream().filter(stage -> "KeycloakModuleResourceCreator".equals(stage.getStageId()))
      .findAny()).isEmpty();
  }

  public static class KongConfigExt extends KongConfiguration {

    @Bean(name = "folioKongAdminClient")
    public KongAdminClient folioKongIntegrationClient(okhttp3.OkHttpClient okHttpClient,
      KongConfigurationProperties properties, Contract contract, Encoder encoder, Decoder decoder,
      RetryConfigurationProperties retryConfig, ThreadLocalModuleStageContext threadLocalModuleStageContext) {
      return mock(KongAdminClient.class);
    }
  }

  public static class TestConfig {

    @Bean
    public FolioModuleEntitleFlowFactory folioModuleEntitleFlowFactory() {
      return new FolioModuleEntitleFlowFactory(mock(FolioModuleInstaller.class), mock(FolioModuleEventPublisher.class),
        mock(SystemUserModuleEventPublisher.class), mock(ScheduledJobModuleEventPublisher.class),
        mock(CapabilitiesModuleEventPublisher.class));
    }

    @Bean
    public KeycloakModuleResourceCreator keycloakModuleResourceCreator() {
      var mock = mock(KeycloakModuleResourceCreator.class);
      when(mock.getId()).thenReturn("KeycloakModuleResourceCreator");
      return mock;
    }

    @Bean
    public FlowStageRepository flowStageRepository() {
      return mock(FlowStageRepository.class);
    }

    @Bean
    public ThreadLocalModuleStageContext threadLocalModuleStageContext() {
      return mock(ThreadLocalModuleStageContext.class);
    }

    @Bean
    public KongGatewayService kongGatewayService() {
      return mock(KongGatewayService.class);
    }

    @Bean
    public OkHttpClient okHttpClient() {
      return mock(OkHttpClient.class);
    }

    @Bean
    public KongConfigurationProperties kongConfigurationProperties() {
      return mock(KongConfigurationProperties.class);
    }

    @Bean
    public RetryConfigurationProperties retryConfigurationProperties() {
      return RetryConfigurationProperties.builder().build();
    }

    @Bean
    public Contract contract() {
      return mock(Contract.class);
    }

    @Bean
    public Encoder encoder() {
      return mock(Encoder.class);
    }

    @Bean
    public Decoder decoder() {
      return mock(Decoder.class);
    }
  }
}
