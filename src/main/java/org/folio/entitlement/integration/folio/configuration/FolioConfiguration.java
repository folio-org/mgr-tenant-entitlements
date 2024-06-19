package org.folio.entitlement.integration.folio.configuration;

import static java.util.Arrays.asList;
import static org.folio.common.utils.FeignClientTlsUtils.getHttpClientBuilder;

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
import org.folio.entitlement.integration.folio.stage.FolioModuleUpdater;
import org.folio.entitlement.integration.kafka.CapabilitiesModuleEventPublisher;
import org.folio.entitlement.integration.kafka.EntitlementEventPublisher;
import org.folio.entitlement.integration.kafka.ScheduledJobModuleEventPublisher;
import org.folio.entitlement.integration.kafka.SystemUserModuleEventPublisher;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.service.ModuleSequenceProvider;
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
    return getHttpClientBuilder(folioClientConfigurationProperties.getTls())
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
   * Creates a {@link FolioModuleInstaller} stage for entitlement flow.
   *
   * @param folioModuleService - {@link FolioModuleService} bean from spring context
   * @return created {@link FolioModuleInstaller} stage
   */
  @Bean
  public FolioModuleUpdater folioModuleUpdater(FolioModuleService folioModuleService,
    EntitlementModuleService entitlementModuleService) {
    return new FolioModuleUpdater(folioModuleService, entitlementModuleService);
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

  /**
   * Creates a {@link FolioModulesFlowProvider} bean for modules flow.
   *
   * @param entitleFlowFactory - {@link FolioModuleEntitleFlowFactory} bean from spring context
   * @param revokeFlowFactory - {@link FolioModuleRevokeFlowFactory} bean from spring context
   * @param upgradeFlowFactory - {@link FolioModuleUpgradeFlowFactory} bean from spring context
   * @return created {@link FolioModulesFlowProvider} flow provider
   */
  @Bean
  public FolioModulesFlowProvider folioModulesFlowProvider(FolioModuleEntitleFlowFactory entitleFlowFactory,
    FolioModuleRevokeFlowFactory revokeFlowFactory, FolioModuleUpgradeFlowFactory upgradeFlowFactory,
    ModuleSequenceProvider moduleSequenceProvider) {
    var factories = asList(entitleFlowFactory, revokeFlowFactory, upgradeFlowFactory);
    return new FolioModulesFlowProvider(factories, moduleSequenceProvider);
  }


  /**
   * Creates a {@link FolioModuleEntitleFlowFactory} bean for module flow.
   *
   * @param folioModuleInstaller - {@link FolioModuleInstaller} bean
   * @param eventPublisher - {@link FolioModuleEventPublisher} bean
   * @param systemUserPublisher - {@link SystemUserModuleEventPublisher} bean
   * @param scheduledJobPublisher - {@link ScheduledJobModuleEventPublisher} bean
   * @param capabilitiesPublisher - {@link CapabilitiesModuleEventPublisher} bean
   * @return created {@link FolioModuleEntitleFlowFactory} bean
   */
  @Bean
  public FolioModuleEntitleFlowFactory folioModuleEntitleFlowFactory(FolioModuleInstaller folioModuleInstaller,
    FolioModuleEventPublisher eventPublisher, SystemUserModuleEventPublisher systemUserPublisher,
    ScheduledJobModuleEventPublisher scheduledJobPublisher, CapabilitiesModuleEventPublisher capabilitiesPublisher) {
    return new FolioModuleEntitleFlowFactory(folioModuleInstaller, eventPublisher,
      systemUserPublisher, scheduledJobPublisher, capabilitiesPublisher);
  }

  /**
   * Creates a {@link FolioModuleRevokeFlowFactory} bean for module flow.
   *
   * @param folioModuleUninstaller - {@link FolioModuleUninstaller} bean
   * @param folioModuleEventPublisher - {@link FolioModuleEventPublisher} bean
   * @return created {@link FolioModuleEntitleFlowFactory} bean
   */
  @Bean
  public FolioModuleRevokeFlowFactory folioModuleRevokeFlowFactory(FolioModuleUninstaller folioModuleUninstaller,
    FolioModuleEventPublisher folioModuleEventPublisher) {
    return new FolioModuleRevokeFlowFactory(folioModuleUninstaller, folioModuleEventPublisher);
  }

  /**
   * Creates a {@link FolioModuleUpgradeFlowFactory} bean for module flow.
   *
   * @return created {@link FolioModuleUpgradeFlowFactory} bean
   */
  @Bean
  public FolioModuleUpgradeFlowFactory folioModuleUpgradeFlowFactory(
    FolioModuleUpdater folioModuleUpdater, FolioModuleEventPublisher folioModuleEventPublisher,
    SystemUserModuleEventPublisher systemUserModuleEventPublisher,
    ScheduledJobModuleEventPublisher scheduledJobModuleEventPublisher,
    CapabilitiesModuleEventPublisher capabilitiesModuleEventPublisher) {
    return new FolioModuleUpgradeFlowFactory(folioModuleUpdater, folioModuleEventPublisher,
      systemUserModuleEventPublisher, scheduledJobModuleEventPublisher, capabilitiesModuleEventPublisher);
  }

  /**
   * Creates {@link FolioModuleEventPublisher} bean for module flow.
   *
   * @param publisher - {@link EntitlementEventPublisher} bean
   * @return created {@link FolioModuleEventPublisher} bean
   */
  @Bean
  public FolioModuleEventPublisher folioModuleEventPublisher(EntitlementEventPublisher publisher) {
    return new FolioModuleEventPublisher(publisher);
  }
}
