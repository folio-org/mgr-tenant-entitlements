package org.folio.entitlement.integration.folio.configuration;

import static java.util.Arrays.asList;

import java.net.http.HttpClient;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.folio.FolioModuleService;
import org.folio.entitlement.integration.folio.FolioTenantApiClient;
import org.folio.entitlement.integration.folio.flow.FolioModuleEntitleFlowFactory;
import org.folio.entitlement.integration.folio.flow.FolioModuleRevokeFlowFactory;
import org.folio.entitlement.integration.folio.flow.FolioModuleUpgradeFlowFactory;
import org.folio.entitlement.integration.folio.flow.FolioModulesFlowProvider;
import org.folio.entitlement.integration.folio.stage.FolioModuleEventPublisher;
import org.folio.entitlement.integration.folio.stage.FolioModuleInstaller;
import org.folio.entitlement.integration.folio.stage.FolioModuleUninstaller;
import org.folio.entitlement.integration.kafka.CapabilitiesModuleEventPublisher;
import org.folio.entitlement.integration.kafka.EntitlementEventPublisher;
import org.folio.entitlement.integration.kafka.ScheduledJobModuleEventPublisher;
import org.folio.entitlement.integration.kafka.SystemUserModuleEventPublisher;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.utils.JsonConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(FolioClientConfigurationProperties.class)
public class FolioConfiguration {

  /**
   * Creates an {@link HttpClient} object to communicate with FOLIO modules.
   *
   * @return created {@link HttpClient} object
   */
  @Bean
  public HttpClient httpClient(FolioClientConfigurationProperties folioClientConfigurationProperties) {
    return HttpClient.newBuilder()
      .connectTimeout(folioClientConfigurationProperties.getConnectTimeout())
      .build();
  }

  /**
   * Creates a {@link FolioTenantApiClient} component to perform initialization calls for FOLIO modules.
   *
   * @param httpClient - {@link HttpClient} bean from spring context
   * @param jsonConverter - {@link JsonConverter} bean from spring context
   * @return created {@link FolioTenantApiClient} bean
   */
  @Bean
  public FolioTenantApiClient folioTenantApiClient(
    HttpClient httpClient, JsonConverter jsonConverter, FolioClientConfigurationProperties clientConfiguration) {
    return new FolioTenantApiClient(httpClient, jsonConverter, clientConfiguration);
  }

  /**
   * Creates a {@link FolioModuleService} component to perform module enable/disable operations.
   *
   * @param apiClient - {@link FolioTenantApiClient} bean from spring context
   * @param moduleService - {@link EntitlementModuleService} bean from spring context
   * @return created {@link FolioModuleService} bean
   */
  @Bean
  public FolioModuleService folioModuleService(FolioTenantApiClient apiClient, EntitlementModuleService moduleService) {
    return new FolioModuleService(apiClient, moduleService);
  }

  /**
   * Creates a {@link FolioModuleInstaller} stage for entitlement flow.
   *
   * @param folioModuleService - {@link FolioModuleService} bean from spring context
   * @return created {@link FolioModuleInstaller} stage
   */
  @Bean
  public FolioModuleInstaller folioModuleInstaller(FolioModuleService folioModuleService) {
    return new FolioModuleInstaller(folioModuleService);
  }

  /**
   * Creates a {@link FolioModuleUninstaller} stage for entitlement flow.
   *
   * @param folioModuleService - {@link FolioModuleService} bean from spring context
   * @return created {@link FolioModuleInstaller} stage
   */
  @Bean
  public FolioModuleUninstaller folioModuleUninstaller(FolioModuleService folioModuleService) {
    return new FolioModuleUninstaller(folioModuleService);
  }

  @Bean
  public FolioModulesFlowProvider folioModulesFlowProvider(FolioModuleEntitleFlowFactory entitleFlowFactory,
    FolioModuleRevokeFlowFactory revokeFlowFactory, FolioModuleUpgradeFlowFactory upgradeFlowFactory) {
    return new FolioModulesFlowProvider(asList(entitleFlowFactory, revokeFlowFactory, upgradeFlowFactory));
  }

  @Bean
  public FolioModuleEntitleFlowFactory folioModuleEntitleFlowFactory(FolioModuleInstaller folioModuleInstaller,
    FolioModuleEventPublisher eventPublisher, SystemUserModuleEventPublisher systemUserPublisher,
    ScheduledJobModuleEventPublisher scheduledJobPublisher, CapabilitiesModuleEventPublisher capabilitiesPublisher) {
    return new FolioModuleEntitleFlowFactory(folioModuleInstaller, eventPublisher,
      systemUserPublisher, scheduledJobPublisher, capabilitiesPublisher);
  }

  @Bean
  public FolioModuleRevokeFlowFactory folioModuleRevokeFlowFactory(FolioModuleUninstaller folioModuleUninstaller,
    FolioModuleEventPublisher folioModuleEventPublisher) {
    return new FolioModuleRevokeFlowFactory(folioModuleUninstaller, folioModuleEventPublisher);
  }

  @Bean
  public FolioModuleUpgradeFlowFactory folioModuleUpgradeFlowFactory() {
    return new FolioModuleUpgradeFlowFactory();
  }

  @Bean
  public FolioModuleEventPublisher kafkaPublisherStage(EntitlementEventPublisher publisher) {
    return new FolioModuleEventPublisher(publisher);
  }
}
