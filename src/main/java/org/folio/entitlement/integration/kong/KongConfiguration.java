package org.folio.entitlement.integration.kong;

import org.folio.tools.kong.service.KongGatewayService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "application.kong", name = "enabled")
public class KongConfiguration {

  /**
   * Creates {@link KongRouteCreator} bean as a flow stage to populate routes in Kong per application.
   *
   * @param kongGatewayService - {@link KongGatewayService} bean from spring context.
   * @return created {@link KongGatewayService} bean
   */
  @Bean
  @ConditionalOnProperty("application.okapi.enabled")
  public KongRouteCreator kongRouteCreator(KongGatewayService kongGatewayService) {
    return new KongRouteCreator(kongGatewayService);
  }

  /**
   * Creates {@link KongRouteUpdater} bean as a flow stage to update routes in Kong per application.
   *
   * @param kongGatewayService - {@link KongGatewayService} bean from spring context.
   * @return created {@link KongRouteUpdater} bean
   */
  @Bean
  @ConditionalOnProperty("application.okapi.enabled")
  public KongRouteUpdater kongRouteUpdater(KongGatewayService kongGatewayService) {
    return new KongRouteUpdater(kongGatewayService);
  }

  /**
   * Creates {@link KongRouteCleaner} bean as a flow stage to clean routes in Kong per application.
   *
   * @param kongGatewayService - {@link KongGatewayService} bean from spring context.
   * @return created {@link KongRouteCleaner} bean
   */
  @Bean
  @ConditionalOnProperty("application.okapi.enabled")
  public KongRouteCleaner kongRouteCleaner(KongGatewayService kongGatewayService) {
    return new KongRouteCleaner(kongGatewayService);
  }

  /**
   * Creates {@link KongModuleRouteCreator} bean as a flow stage to populate routes in Kong per module.
   *
   * @param kongGatewayService - {@link KongGatewayService} bean from spring context.
   * @return created {@link KongModuleRouteCreator} bean
   */
  @Bean
  @ConditionalOnProperty(value = "application.okapi.enabled", havingValue = "false")
  public KongModuleRouteCreator kongModuleRouteCreator(KongGatewayService kongGatewayService) {
    return new KongModuleRouteCreator(kongGatewayService);
  }

  /**
   * Creates {@link KongModuleRouteUpdater} bean as a flow stage to update routes in Kong per module.
   *
   * @param kongGatewayService - {@link KongGatewayService} bean from spring context.
   * @return created {@link KongModuleRouteUpdater} bean
   */
  @Bean
  @ConditionalOnProperty(value = "application.okapi.enabled", havingValue = "false")
  public KongModuleRouteUpdater kongModuleRouteUpdater(KongGatewayService kongGatewayService) {
    return new KongModuleRouteUpdater(kongGatewayService);
  }

  /**
   * Creates {@link KongModuleRouteCleaner} bean as a flow stage to clean in Kong per module.
   *
   * @param kongGatewayService - {@link KongGatewayService} bean from spring context.
   * @return created {@link KongModuleRouteCleaner} bean
   */
  @Bean
  @ConditionalOnProperty(value = "application.okapi.enabled", havingValue = "false")
  public KongModuleRouteCleaner kongModuleRouteCleaner(KongGatewayService kongGatewayService) {
    return new KongModuleRouteCleaner(kongGatewayService);
  }
}
