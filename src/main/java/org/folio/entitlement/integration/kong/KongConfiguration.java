package org.folio.entitlement.integration.kong;

import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.tools.kong.service.KongGatewayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "application.kong.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ApiGatewayConfigurationProperties.class)
public class KongConfiguration {

  @Bean
  @ConditionalOnProperty(value = "application.okapi.enabled", havingValue = "false")
  public KongModuleRouteCreator kongModuleRouteCreator(KongGatewayService kongGatewayService,
    ApiGatewayConfigurationProperties properties, EntitlementModuleService entitlementModuleService) {
    return new KongModuleRouteCreator(kongGatewayService, properties, entitlementModuleService);
  }

  @Bean
  @ConditionalOnProperty(value = "application.okapi.enabled", havingValue = "false")
  public KongModuleRouteUpdater kongModuleRouteUpdater(KongGatewayService kongGatewayService,
    ApiGatewayConfigurationProperties properties, EntitlementModuleService entitlementModuleService) {
    return new KongModuleRouteUpdater(kongGatewayService, properties, entitlementModuleService);
  }

  @Bean
  @ConditionalOnProperty(value = "application.okapi.enabled", havingValue = "false")
  public KongModuleRouteCleaner kongModuleRouteCleaner(KongGatewayService kongGatewayService,
    ApiGatewayConfigurationProperties properties, EntitlementModuleService entitlementModuleService) {
    return new KongModuleRouteCleaner(kongGatewayService, properties, entitlementModuleService);
  }
}
