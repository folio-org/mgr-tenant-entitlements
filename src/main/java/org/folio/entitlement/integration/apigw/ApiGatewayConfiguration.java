package org.folio.entitlement.integration.apigw;

import jakarta.annotation.PostConstruct;
import java.util.Set;
import org.folio.common.gateway.ApiGatewayService;
import org.folio.entitlement.service.EntitlementModuleService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "application.apigw.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(ApiGatewayConfigurationProperties.class)
public class ApiGatewayConfiguration {

  private static final Set<String> SUPPORTED_GATEWAY_TYPES = Set.of("kong", "apisix");

  @Value("${application.apigw.type:kong}")
  private String gatewayType;

  @PostConstruct
  void validateGatewayType() {
    validateGatewayType(gatewayType);
  }

  static void validateGatewayType(String type) {
    if (!SUPPORTED_GATEWAY_TYPES.contains(type)) {
      throw new IllegalStateException("Unsupported API Gateway type: '" + type
        + "'. Supported values: kong, apisix (application.apigw.type / APIGW_TYPE)");
    }
  }

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
