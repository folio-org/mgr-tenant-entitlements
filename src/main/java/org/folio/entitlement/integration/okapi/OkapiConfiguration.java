package org.folio.entitlement.integration.okapi;

import feign.Contract;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;
import org.folio.entitlement.integration.kafka.EntitlementEventPublisher;
import org.folio.entitlement.integration.kong.KongAdminClient;
import org.folio.entitlement.service.EntitlementModuleService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBean(OkapiConfigurationProperties.class)
public class OkapiConfiguration {

  /**
   * Creates a {@link org.springframework.cloud.openfeign.FeignClient} for integration with Kong Admin API.
   *
   * @param okapiConfigurationProperties - okapi configuration properties with required data
   * @param contract - feign contract
   * @param encoder - feign http body encoder
   * @param decoder - feign http body decoder
   * @return created {@link KongAdminClient} component
   */
  @Bean
  public OkapiClient okapiClient(OkapiConfigurationProperties okapiConfigurationProperties,
    Contract contract, Encoder encoder, Decoder decoder) {
    return Feign.builder()
      .contract(contract)
      .encoder(encoder)
      .decoder(decoder)
      .target(OkapiClient.class, okapiConfigurationProperties.getUrl());
  }

  /**
   * Creates {@link OkapiModulesInstaller} bean as a flow stage to implement integration with okapi.
   *
   * @param okapiClient - {@link OkapiClient} bean from spring context.
   * @return created {@link OkapiModulesInstaller} bean
   */
  @Bean
  public OkapiModulesInstaller okapiModuleInstaller(OkapiClient okapiClient, EntitlementModuleService moduleService) {
    return new OkapiModulesInstaller(okapiClient, moduleService);
  }

  @Bean
  public ModulesEventPublisherStage modulesEventPublisherStage(EntitlementEventPublisher publisher) {
    return new ModulesEventPublisherStage(publisher);
  }

  @Bean
  public OkapiModuleInstallerFlowProvider okapiApplicationInstallerFlowProvider(
    OkapiModulesInstaller moduleInstaller, ModulesEventPublisherStage modulesEventPublisherStage) {
    return new OkapiModuleInstallerFlowProvider(moduleInstaller, modulesEventPublisherStage);
  }
}
