package org.folio.entitlement.integration.folio;

import java.net.http.HttpClient;
import lombok.RequiredArgsConstructor;
import org.folio.entitlement.integration.kafka.EntitlementEventPublisher;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.utils.JsonConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(FolioClientConfiguration.class)
public class FolioConfiguration {

  /**
   * Creates an {@link HttpClient} object to communicate with FOLIO modules.
   *
   * @return created {@link HttpClient} object
   */
  @Bean
  public HttpClient httpClient(FolioClientConfiguration folioClientConfiguration) {
    return HttpClient.newBuilder()
      .connectTimeout(folioClientConfiguration.getConnectTimeout())
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
    HttpClient httpClient, JsonConverter jsonConverter, FolioClientConfiguration clientConfiguration) {
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
  public ModuleInstallationFlowProvider folioModuleInstallerFlowProvider(FolioModuleInstaller moduleInstaller,
    FolioModuleUninstaller moduleUninstaller, FolioModuleEventPublisher folioModuleEventPublisher) {
    return new ModuleInstallationFlowProvider(moduleInstaller, moduleUninstaller, folioModuleEventPublisher);
  }

  @Bean
  public FolioModuleEventPublisher kafkaPublisherStage(EntitlementEventPublisher publisher) {
    return new FolioModuleEventPublisher(publisher);
  }
}
