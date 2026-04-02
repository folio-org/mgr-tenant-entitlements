package org.folio.entitlement.integration.okapi.configuration;

import static java.util.Arrays.asList;
import static org.folio.common.utils.tls.HttpClientTlsUtils.buildHttpServiceClient;

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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestClient;

@Configuration
@ConditionalOnProperty("application.okapi.enabled")
public class OkapiConfiguration {

  /**
   * Creates an {@link OkapiClient} for integration with Okapi API.
   *
   * @param okapiConfigurationProperties - okapi configuration properties with required data
   * @return created {@link OkapiClient} component
   */
  @Bean
  public OkapiClient okapiClient(OkapiConfigurationProperties okapiConfigurationProperties) {
    return buildHttpServiceClient(
      RestClient.builder().baseUrl(okapiConfigurationProperties.getUrl()),
      null, okapiConfigurationProperties.getUrl(), OkapiClient.class);
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
  public OkapiModulesUpgradeFlowFactory upgradeOkapiModulesFlowFactory(
    SystemUserEventPublisher systemUserEventPublisher,
    ScheduledJobEventPublisher scheduledJobEventPublisher,
    CapabilitiesEventPublisher capabilitiesEventPublisher) {
    return new OkapiModulesUpgradeFlowFactory(
      systemUserEventPublisher, scheduledJobEventPublisher, capabilitiesEventPublisher);
  }
}
