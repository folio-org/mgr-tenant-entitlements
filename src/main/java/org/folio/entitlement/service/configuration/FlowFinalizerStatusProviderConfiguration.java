package org.folio.entitlement.service.configuration;

import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.stage.FlowFinalizerStageAwareStatusProvider;
import org.folio.entitlement.service.stage.FlowFinalizerStatusProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlowFinalizerStatusProviderConfiguration {

  @Bean
  public FlowFinalizerStatusProvider<CommonStageContext> flowFinalizerStatusProvider(FlowRepository flowRepository) {
    return new FlowFinalizerStageAwareStatusProvider<>(flowRepository);
  }

  @Bean
  public FlowFinalizerStatusProvider<ApplicationStageContext> applicationFlowFinalizerStatusProvider(
    ApplicationFlowRepository applicationFlowRepository) {
    return new FlowFinalizerStageAwareStatusProvider<>(applicationFlowRepository);
  }
}
