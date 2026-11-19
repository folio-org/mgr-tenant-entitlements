package org.folio.entitlement.integration.folio;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.integration.folio.model.TenantOperation;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@UnitTest
@SpringJUnitConfig(FolioModuleServiceTransactionTest.TestConfig.class)
class FolioModuleServiceTransactionTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String JOB_ID = "job-1";

  @Autowired private FolioModuleService folioModuleService;
  @Autowired private FolioTenantApiClient apiClient;
  @Autowired private EntitlementModuleService moduleService;
  @Autowired private TenantJobPoller tenantJobPoller;
  @Autowired private TransactionProbe transactionProbe;

  @AfterEach
  void resetMocks() {
    reset(apiClient, moduleService, tenantJobPoller);
  }

  @Test
  void enable_positive_noTransactionIsActiveWhilePolling() {
    var request = moduleRequest(false);
    var operation = TenantOperation.accepted(JOB_ID);
    var transactionActive = new AtomicBoolean(true);
    when(moduleService.findAllModuleEntitlements(MODULE_ID, TENANT_ID)).thenReturn(emptyList());
    when(apiClient.install(request)).thenReturn(operation);
    doAnswer(invocation -> {
      transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
      return null;
    }).when(tenantJobPoller).await(request, operation);

    folioModuleService.enable(request);

    assertThat(transactionActive).isFalse();
  }

  @Test
  void disable_positive_noTransactionIsActiveWhilePolling() {
    var request = moduleRequest(true);
    var operation = TenantOperation.accepted(JOB_ID);
    var transactionActive = new AtomicBoolean(true);
    when(moduleService.findAllModuleEntitlements(MODULE_ID, TENANT_ID)).thenReturn(List.of(entitlement()));
    when(apiClient.uninstall(request)).thenReturn(operation);
    doAnswer(invocation -> {
      transactionActive.set(TransactionSynchronizationManager.isActualTransactionActive());
      return null;
    }).when(tenantJobPoller).await(request, operation);

    folioModuleService.disable(request);

    assertThat(transactionActive).isFalse();
  }

  @Test
  void transactionProbe_positive_transactionIsActiveInTransactionalMethod() {
    assertThat(transactionProbe.isTransactionActive()).isTrue();
  }

  private static ModuleRequest moduleRequest(boolean purge) {
    var tenantInterface = new InterfaceDescriptor().id("_tenant").version("2.0").interfaceType("system");
    return ModuleRequest.builder()
      .applicationId(APPLICATION_ID)
      .moduleId(MODULE_ID)
      .location("http://mod-foo:8081")
      .tenantId(TENANT_ID)
      .tenantName(TENANT_NAME)
      .purge(purge)
      .tenantParameters(emptyList())
      .tenantInterface(tenantInterface)
      .build();
  }

  @Configuration
  @EnableTransactionManagement
  static class TestConfig {

    @Bean
    PlatformTransactionManager transactionManager() {
      return new NoOpTransactionManager();
    }

    @Bean
    FolioTenantApiClient folioTenantApiClient() {
      return mock(FolioTenantApiClient.class);
    }

    @Bean
    EntitlementModuleService entitlementModuleService() {
      return mock(EntitlementModuleService.class);
    }

    @Bean
    TenantJobPoller tenantJobPoller() {
      return mock(TenantJobPoller.class);
    }

    @Bean
    FolioModuleService folioModuleService(FolioTenantApiClient apiClient, EntitlementModuleService moduleService,
      TenantJobPoller tenantJobPoller) {
      return new FolioModuleService(apiClient, moduleService, tenantJobPoller);
    }

    @Bean
    TransactionProbe transactionProbe() {
      return new TransactionProbe();
    }
  }

  static class TransactionProbe {

    @Transactional
    boolean isTransactionActive() {
      return TransactionSynchronizationManager.isActualTransactionActive();
    }
  }

  private static final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
      // no resources to bind
    }

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      // nothing to commit
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      // nothing to roll back
    }
  }
}
