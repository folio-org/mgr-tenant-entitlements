package org.folio.entitlement.integration.kong;

import static org.folio.common.utils.tls.FeignClientTlsUtils.getOkHttpClient;

import feign.Contract;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;
import feign.codec.ErrorDecoder;
import org.folio.entitlement.configuration.RetryConfigurationProperties;
import org.folio.entitlement.retry.feign.FeignRetrySupportingErrorDecoder;
import org.folio.entitlement.retry.feign.FeignRetryer;
import org.folio.entitlement.service.RetryInformationService;
import org.folio.entitlement.service.stage.ThreadLocalModuleStageContext;
import org.folio.tools.kong.client.KongAdminClient;
import org.folio.tools.kong.configuration.KongConfigurationProperties;
import org.folio.tools.kong.service.KongGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "application.kong", name = "enabled")
public class KongConfiguration {

  @Autowired
  private ThreadLocalModuleStageContext threadLocalModuleStageContext;

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
    return new KongModuleRouteCreator(kongGatewayService, threadLocalModuleStageContext);
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
    return new KongModuleRouteUpdater(kongGatewayService, threadLocalModuleStageContext);
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
    return new KongModuleRouteCleaner(kongGatewayService, threadLocalModuleStageContext);
  }


  /**
   * Creates a {@link org.springframework.cloud.openfeign.FeignClient} for integration with Kong Admin API.
   *
   * @param properties - kong configuration properties with required data
   * @param contract - feign contract
   * @param encoder - feign http body encoder
   * @param decoder - feign http body decoder
   * @return created {@link KongAdminClient} component
   */
  @Bean(name = "folioKongAdminClient")
  public KongAdminClient folioKongIntegrationClient(okhttp3.OkHttpClient okHttpClient,
    KongConfigurationProperties properties, Contract contract, Encoder encoder, Decoder decoder,
    RetryConfigurationProperties retryConfig, RetryInformationService retryInformationService) {

    var feignClientBuilder = Feign.builder().contract(contract).encoder(encoder).decoder(decoder).errorDecoder(
      new FeignRetrySupportingErrorDecoder(new ErrorDecoder.Default(),
        methodKeyAndResponse -> methodKeyAndResponse.getRight().status() == 500, "Kong HTTP request",
        "Internal Server Error", threadLocalModuleStageContext, retryInformationService));

    feignClientBuilder = feignClientBuilder.client(getOkHttpClient(okHttpClient, properties.getTls())).retryer(
      new FeignRetryer(retryConfig.getKong().getBackoff().getDelay(), retryConfig.getKong().getBackoff().getMaxdelay(),
        retryConfig.getKong().getMax(), retryableException -> retryableException.status() >= 500,
        "Kong HTTP request", "HTTP status 500 (Internal Server Error)",
        threadLocalModuleStageContext, retryInformationService));

    return feignClientBuilder.target(KongAdminClient.class, properties.getUrl());
  }
}
