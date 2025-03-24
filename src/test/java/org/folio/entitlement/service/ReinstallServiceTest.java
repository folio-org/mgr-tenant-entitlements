package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.ResultList;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.am.model.ModuleDiscovery;
import org.folio.entitlement.integration.folio.FolioModuleService;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.integration.tm.TenantManagerService;
import org.folio.entitlement.integration.tm.model.Tenant;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ReinstallServiceTest {

  @InjectMocks private ReinstallService unit;

  @Mock private Supplier<ExecutorService> executorSupplier;
  @Mock private FolioModuleService folioModuleService;
  @Mock private TenantManagerService tenantManagerService;
  @Mock private ApplicationManagerService applicationManagerService;

  private final UUID tenantUuid = UUID.fromString("6CFEE887-4738-4016-BF4C-0C8828970898");
  private final String authToken = "test-auth-token";

  @BeforeEach
  public void setupTenantMock() {
    when(executorSupplier.get()).then(inv -> Executors.newSingleThreadExecutor());
    when(tenantManagerService.findTenant(tenantUuid, authToken)).thenReturn(
      Tenant.of(tenantUuid, "test", "Unit tests tenant"));
  }

  @Test
  void testAppsReinstall() {
    var appId1 = "app1";
    List<String> modules = List.of("m1", "m2", "m3", "m4");
    var appDesc1 = mockModuleData(appId1, modules);
    var appId2 = "app2";
    List<String> modules2 = List.of("m1_2", "m2_2", "m3_2", "m4_2");
    var appDesc2 = mockModuleData(appId2, modules2);
    when(applicationManagerService.getApplicationDescriptors(List.of(appId1, appId2), authToken)).thenReturn(
      List.of(appDesc1, appDesc2));

    doAnswer(inv -> {
      var moduleRequest = inv.getArgument(0, ModuleRequest.class);
      if (moduleRequest.getModuleId().startsWith("m2") || moduleRequest.getModuleId().startsWith("m3")) {
        throw new RuntimeException("Test Exception");
      }
      return null;
    }).when(folioModuleService).installModule(any());

    var result = unit.reinstallApplications(authToken, List.of(appId1, appId2), tenantUuid, null);
    assertThat(result.getEntitlements()).hasSize(4);
    assertThat(result.getErrors()).hasSize(4);
    assertThat(result.getEntitlements()).containsAll(List.of("m1", "m4", "m1_2", "m4_2"));
    assertThat(result.getErrors().get(0)).startsWith(
      "Error re-installing module m2 - RuntimeException Test Exception.\njava.lang.RuntimeException: Test Exception");
    assertThat(result.getErrors().get(1)).startsWith(
      "Error re-installing module m3 - RuntimeException Test Exception.\njava.lang.RuntimeException: Test Exception");
    assertThat(result.getErrors().get(2)).startsWith(
      "Error re-installing module m2_2 - RuntimeException Test Exception.\njava.lang.RuntimeException: Test Exception");
    assertThat(result.getErrors().get(3)).startsWith(
      "Error re-installing module m3_2 - RuntimeException Test Exception.\njava.lang.RuntimeException: Test Exception");
  }

  @Test
  void testModulesReinstall() {
    var appId = "mock-app";
    List<String> modules = List.of("m1", "m2", "m3", "m4");
    mockModuleData(appId, modules);

    doAnswer(inv -> {
      var moduleRequest = inv.getArgument(0, ModuleRequest.class);
      if (moduleRequest.getModuleId().equals("m2") || moduleRequest.getModuleId().equals("m3")) {
        throw new RuntimeException("Test Exception");
      }
      return null;
    }).when(folioModuleService).installModule(any());

    var result = unit.reinstallModules(authToken, appId, modules, tenantUuid, null);
    assertThat(result.getEntitlements()).hasSize(2);
    assertThat(result.getErrors()).hasSize(2);
    assertThat(result.getEntitlements()).containsAll(List.of("m1", "m4"));
    assertThat(result.getErrors().get(0)).startsWith(
      "Error re-installing module m2 - RuntimeException Test Exception.\njava.lang.RuntimeException: Test Exception");
    assertThat(result.getErrors().get(1)).startsWith(
      "Error re-installing module m3 - RuntimeException Test Exception.\njava.lang.RuntimeException: Test Exception");
  }

  private ApplicationDescriptor mockModuleData(String appId, List<String> modules) {
    var moduleDiscoveries = ResultList.of(1,
      modules.stream().map(m -> ModuleDiscovery.builder().id(m).name(m).location("location " + m).build()).toList());
    when(applicationManagerService.getModuleDiscoveries(appId, authToken)).thenReturn(moduleDiscoveries);
    var applicationDescriptor = new ApplicationDescriptor();
    applicationDescriptor.setId(appId);
    applicationDescriptor.setModuleDescriptors(modules.stream().map(module -> {
      var result = new ModuleDescriptor();
      result.setId(module);
      return result;
    }).toList());
    when(applicationManagerService.getApplicationDescriptor(appId, authToken)).thenReturn(applicationDescriptor);
    return applicationDescriptor;
  }
}
