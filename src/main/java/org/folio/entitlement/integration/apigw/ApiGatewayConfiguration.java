package org.folio.entitlement.integration.apigw;

import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.tools.kong.service.KongGatewayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "application.apigw.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ApiGatewayConfigurationProperties.class)
public class ApiGatewayConfiguration {

  @Bean
  public ApiGatewayModuleRouteCreator apiGatewayModuleRouteCreator(KongGatewayService kongGatewayService,
    ApiGatewayConfigurationProperties properties, EntitlementModuleService entitlementModuleService) {
    return new ApiGatewayModuleRouteCreator(kongGatewayService, properties, entitlementModuleService);
  }

  @Bean
  public ApiGatewayModuleRouteUpdater apiGatewayModuleRouteUpdater(KongGatewayService kongGatewayService,
    ApiGatewayConfigurationProperties properties, EntitlementModuleService entitlementModuleService) {
    return new ApiGatewayModuleRouteUpdater(kongGatewayService, properties, entitlementModuleService);
  }

  @Bean
  public ApiGatewayModuleRouteCleaner apiGatewayModuleRouteCleaner(KongGatewayService kongGatewayService,
    ApiGatewayConfigurationProperties properties, EntitlementModuleService entitlementModuleService) {
    return new ApiGatewayModuleRouteCleaner(kongGatewayService, properties, entitlementModuleService);
  }
}
