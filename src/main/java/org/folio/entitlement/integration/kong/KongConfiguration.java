package org.folio.entitlement.integration.kong;

import feign.Contract;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@ConditionalOnBean(KongConfigurationProperties.class)
@Import(FeignClientsConfiguration.class)
public class KongConfiguration {

  /**
   * Creates a {@link org.springframework.cloud.openfeign.FeignClient} for integration with Kong Admin API.
   *
   * @param kongConfigurationProperties - kong configuration properties with required data
   * @param contract - feign contract
   * @param encoder - feign http body encoder
   * @param decoder - feign http body decoder
   * @return created {@link KongAdminClient} component
   */
  @Bean
  public KongAdminClient kongAdminClient(KongConfigurationProperties kongConfigurationProperties,
    Contract contract, Encoder encoder, Decoder decoder) {
    return Feign.builder()
      .contract(contract)
      .encoder(encoder)
      .decoder(decoder)
      .target(KongAdminClient.class, kongConfigurationProperties.getUrl());
  }

  /**
   * Creates {@link KongGatewayService} bean for integration with Kong Admin API.
   *
   * @param kongAdminClient - {@link KongAdminClient} bean from spring context.
   * @return created {@link KongGatewayService} bean
   */
  @Bean
  public KongGatewayService kongGatewayService(KongAdminClient kongAdminClient) {
    return new KongGatewayService(kongAdminClient);
  }

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
