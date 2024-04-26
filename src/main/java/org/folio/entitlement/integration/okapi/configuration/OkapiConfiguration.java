package org.folio.entitlement.integration.okapi.configuration;

import static java.util.Arrays.asList;

import feign.Contract;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;
import org.folio.entitlement.integration.kafka.CapabilitiesEventPublisher;
import org.folio.entitlement.integration.kafka.EntitlementEventPublisher;
import org.folio.entitlement.integration.kafka.ScheduledJobEventPublisher;
import org.folio.entitlement.integration.kafka.SystemUserEventPublisher;
import org.folio.entitlement.integration.okapi.OkapiClient;
import org.folio.entitlement.integration.okapi.flow.OkapiModulesEntitleFlowFactory;
import org.folio.entitlement.integration.okapi.flow.OkapiModulesFlowFactory;
import org.folio.entitlement.integration.okapi.flow.OkapiModulesFlowProvider;
import org.folio.entitlement.integration.okapi.flow.OkapiModulesRevokeFlowFactory;
import org.folio.entitlement.integration.okapi.flow.OkapiModulesUpgradeFlowFactory;
import org.folio.entitlement.integration.okapi.stage.OkapiModulesEventPublisher;
import org.folio.entitlement.integration.okapi.stage.OkapiModulesInstaller;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.ModuleSequenceProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

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
   * @return created {@link OkapiClient} component
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
  public OkapiModulesEventPublisher okapiModulesEventPublisher(EntitlementEventPublisher publisher) {
    return new OkapiModulesEventPublisher(publisher);
  }

  /**
   * Creates {@link OkapiModulesFlowFactory} bean.
   *
   * <p>This flow factory has higher priority over folio modules flow factory</p>
   *
   * @param upgradeFlowFactory - beans implementing {@link OkapiModulesFlowFactory} interface
   * @return created {@link OkapiModulesFlowFactory} bean
   */
  @Bean
  @Primary
  public OkapiModulesFlowProvider okapiModulesFlowFactory(OkapiModulesEntitleFlowFactory entitleFlowFactory,
    OkapiModulesRevokeFlowFactory revokeFlowFactory, OkapiModulesUpgradeFlowFactory upgradeFlowFactory,
    ModuleSequenceProvider moduleSequenceProvider) {
    var factories = asList(entitleFlowFactory, revokeFlowFactory, upgradeFlowFactory);
    return new OkapiModulesFlowProvider(moduleSequenceProvider, factories);
  }

  /**
   * Creates {@link OkapiModulesEntitleFlowFactory} bean.
   *
   * @param moduleInstaller - {@link OkapiModulesInstaller} bean
   * @param eventPublisherStage - {@link OkapiModulesEventPublisher} bean
   * @return created {@link OkapiModulesEntitleFlowFactory} bean
   */
  @Bean
  public OkapiModulesEntitleFlowFactory uninstallOkapiModulesFlowFactory(
    OkapiModulesInstaller moduleInstaller, OkapiModulesEventPublisher eventPublisherStage,
    ScheduledJobEventPublisher scheduledJobEventPublisher, CapabilitiesEventPublisher capabilitiesEventPublisher,
    SystemUserEventPublisher systemUserEventPublisher) {
    return new OkapiModulesEntitleFlowFactory(moduleInstaller, systemUserEventPublisher,
      scheduledJobEventPublisher, capabilitiesEventPublisher, eventPublisherStage);
  }

  /**
   * Creates {@link OkapiModulesRevokeFlowFactory} bean.
   *
   * @param moduleInstaller - {@link OkapiModulesInstaller} bean
   * @param eventPublisherStage - {@link OkapiModulesEventPublisher} bean
   * @return created {@link OkapiModulesRevokeFlowFactory} bean
   */
  @Bean
  public OkapiModulesRevokeFlowFactory installOkapiModulesFlowFactory(
    OkapiModulesInstaller moduleInstaller, OkapiModulesEventPublisher eventPublisherStage) {
    return new OkapiModulesRevokeFlowFactory(moduleInstaller, eventPublisherStage);
  }


  /**
   * Creates {@link OkapiModulesUpgradeFlowFactory} bean.
   *
   * @return created {@link OkapiModulesUpgradeFlowFactory} bean
   */
  @Bean
  public OkapiModulesUpgradeFlowFactory upgradeOkapiModulesFlowFactory() {
    return new OkapiModulesUpgradeFlowFactory();
  }
}
