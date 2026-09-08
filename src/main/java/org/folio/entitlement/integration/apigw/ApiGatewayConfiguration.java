package org.folio.entitlement.integration.apigw;

import org.folio.common.gateway.ApiGatewayService;
import org.folio.entitlement.service.EntitlementModuleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "application.apigw.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ApiGatewayConfigurationProperties.class)
public class ApiGatewayConfiguration {

  @Bean
  public ApiGatewayModuleRouteCreator apiGatewayModuleRouteCreator(ApiGatewayService apiGatewayService,
    ApiGatewayConfigurationProperties properties, EntitlementModuleService entitlementModuleService) {
    return new ApiGatewayModuleRouteCreator(apiGatewayService, properties, entitlementModuleService);
  }

  @Bean
  public ApiGatewayModuleRouteUpdater apiGatewayModuleRouteUpdater(ApiGatewayService apiGatewayService,
    ApiGatewayConfigurationProperties properties, EntitlementModuleService entitlementModuleService) {
    return new ApiGatewayModuleRouteUpdater(apiGatewayService, properties, entitlementModuleService);
  }

  @Bean
  public ApiGatewayModuleRouteCleaner apiGatewayModuleRouteCleaner(ApiGatewayService apiGatewayService,
    ApiGatewayConfigurationProperties properties, EntitlementModuleService entitlementModuleService) {
    return new ApiGatewayModuleRouteCleaner(apiGatewayService, properties, entitlementModuleService);
  }
}
