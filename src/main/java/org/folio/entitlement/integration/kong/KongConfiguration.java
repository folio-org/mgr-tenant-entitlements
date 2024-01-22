package org.folio.entitlement.integration.kong;

import org.folio.tools.kong.service.KongGatewayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "application.kong", name = "enabled")
public class KongConfiguration {

  /**
   * Creates {@link KongRouteCreator} bean as a flow stage to populate routes in Kong.
   *
   * @param kongGatewayService - {@link KongGatewayService} bean from spring context.
   * @return created {@link KongGatewayService} bean
   */
  @Bean
  public KongRouteCreator kongRouteCreator(KongGatewayService kongGatewayService) {
    return new KongRouteCreator(kongGatewayService);
  }

  /**
   * Creates {@link KongRouteCleaner} bean as a flow stage to populate routes in Kong.
   *
   * @param kongGatewayService - {@link KongGatewayService} bean from spring context.
   * @return created {@link KongRouteCleaner} bean
   */
  @Bean
  public KongRouteCleaner kongRouteCleaner(KongGatewayService kongGatewayService) {
    return new KongRouteCleaner(kongGatewayService);
  }
}
