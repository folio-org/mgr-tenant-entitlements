package org.folio.entitlement.integration.kong;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Map;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(properties = {"routemanagement.enable=false"})
@ContextConfiguration(classes = {KongConfiguration.class, KongRouteManagementDisabledTest.TestConfig.class})
class KongRouteManagementDisabledTest {

  @Autowired private FolioModuleEntitleFlowFactory flowFactory;

  @Test
  void testFlowFactory() {
    var flow = flowFactory.createModuleFlow("123", FlowExecutionStrategy.IGNORE_ON_ERROR, Map.of());
    // Check that we have a keycloak module resource creator stage, but not a ResourceCreatorParallelStage
    // stage that does both Kong routes and Keycloak resources creation
    assertThat(flow.getStages().stream().filter(stage -> "ResourceCreatorParallelStage".equals(stage.getStageId()))
      .findAny()).isEmpty();
    assertThat(flow.getStages().stream().filter(stage -> "KeycloakModuleResourceCreator".equals(stage.getStageId()))
      .findAny()).isPresent();
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
  }
}
