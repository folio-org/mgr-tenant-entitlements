package org.folio.entitlement.integration.folio;

import static java.util.Collections.emptyList;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.support.TestUtils;
import org.folio.security.domain.model.descriptor.InterfaceDescriptor;
import org.folio.security.domain.model.descriptor.RoutingEntry;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FolioModuleServiceTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String MODULE_URL = "http://mod-foo:8081";
  private static final List<Entitlement> MODULE_ENTITLEMENT = List.of(entitlement());

  @InjectMocks private FolioModuleService folioModuleService;
  @Mock private FolioTenantApiClient folioTenantApiClient;
  @Mock private EntitlementModuleService moduleService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  private static ModuleRequest moduleRequest(InterfaceDescriptor tenantApi) {
    return moduleRequest(emptyList(), tenantApi, false);
  }

  private static ModuleRequest moduleRequest(InterfaceDescriptor tenantApi, boolean purge) {
    return moduleRequest(emptyList(), tenantApi, purge);
  }

  private static ModuleRequest moduleRequest(List<Parameter> parameters, InterfaceDescriptor tenantApi, boolean purge) {
    return ModuleRequest.builder()
      .applicationId(APPLICATION_ID)
      .moduleId(MODULE_ID)
      .location(MODULE_URL)
      .tenantId(TENANT_ID)
      .tenantName(TENANT_NAME)
      .purge(purge)
      .tenantParameters(parameters)
      .tenantInterface(tenantApi)
      .build();
  }

  private static InterfaceDescriptor tenantInterface() {
    var routingEntries = List.of(
      new RoutingEntry().methods(List.of("POST")).pathPattern("/_/tenant"),
      new RoutingEntry().methods(List.of("GET", "DELETE")).pathPattern("/_/tenant/{id}")
    );

    return new InterfaceDescriptor()
      .version("2.0")
      .id("_tenant")
      .interfaceType("system")
      .handlers(routingEntries);
  }

  @Nested
  @DisplayName("enable")
  class Enable {

    @Test
    void positive() {
      when(moduleService.findAllModuleEntitlements(MODULE_ID, TENANT_ID)).thenReturn(emptyList());

      var tenantParameters = List.of(new Parameter().key("loadSamples").value("true"));
      var moduleRequest = moduleRequest(tenantParameters, tenantInterface(), false);
      folioModuleService.enable(moduleRequest);

      verify(folioTenantApiClient).install(moduleRequest);
      verify(moduleService).save(moduleRequest);
    }

    @Test
    void positive_moduleWithoutTenantApi() {
      when(moduleService.findAllModuleEntitlements(MODULE_ID, TENANT_ID)).thenReturn(emptyList());

      var moduleRequest = moduleRequest(emptyList(), null, false);
      folioModuleService.enable(moduleRequest);

      verify(moduleService).save(moduleRequest);
      verify(folioTenantApiClient, never()).install(any(ModuleRequest.class));
    }

    @Test
    void positive_alreadyEnabled() {
      when(moduleService.findAllModuleEntitlements(MODULE_ID, TENANT_ID)).thenReturn(MODULE_ENTITLEMENT);

      var moduleRequest = moduleRequest(emptyList(), tenantInterface(), false);
      folioModuleService.enable(moduleRequest);

      verify(moduleService, never()).save(any(ModuleRequest.class));
      verifyNoInteractions(folioTenantApiClient);
    }

    @Test
    void positive_moduleEnabledForAnotherApp() {
      var existingEntitlements = List.of(entitlement("another-app"));
      when(moduleService.findAllModuleEntitlements(MODULE_ID, TENANT_ID)).thenReturn(existingEntitlements);

      var moduleRequest = moduleRequest(emptyList(), tenantInterface(), false);
      folioModuleService.enable(moduleRequest);

      verify(moduleService).save(moduleRequest);
      verifyNoInteractions(folioTenantApiClient);
    }
  }

  @Nested
  @DisplayName("disable")
  class Disable {

    @Test
    void positive() {
      when(moduleService.findAllModuleEntitlements(MODULE_ID, TENANT_ID)).thenReturn(MODULE_ENTITLEMENT);

      var moduleRequest = moduleRequest(tenantInterface(), true);
      folioModuleService.disable(moduleRequest);

      verify(folioTenantApiClient).uninstall(moduleRequest);
      verify(moduleService).deleteModuleEntitlement(moduleRequest);
    }

    @Test
    void positive_moduleWithoutTenantApi() {
      when(moduleService.findAllModuleEntitlements(MODULE_ID, TENANT_ID)).thenReturn(MODULE_ENTITLEMENT);

      var moduleRequest = moduleRequest(null, true);
      folioModuleService.disable(moduleRequest);

      verify(folioTenantApiClient, never()).uninstall(any(ModuleRequest.class));
      verify(moduleService).deleteModuleEntitlement(moduleRequest);
    }

    @Test
    void positive_purgeIsFalse() {
      when(moduleService.findAllModuleEntitlements(MODULE_ID, TENANT_ID)).thenReturn(MODULE_ENTITLEMENT);

      var moduleRequest = moduleRequest(legacyTenantInterface(), false);
      folioModuleService.disable(moduleRequest);

      verify(folioTenantApiClient, never()).uninstall(any());
      verify(folioTenantApiClient).disableLegacy(moduleRequest);
      verify(moduleService).deleteModuleEntitlement(moduleRequest);
    }

    @Test
    void positive_legacyTenantApi() {
      when(moduleService.findAllModuleEntitlements(MODULE_ID, TENANT_ID)).thenReturn(MODULE_ENTITLEMENT);

      var expectedModuleRequest = moduleRequest(legacyTenantInterface(), false);
      folioModuleService.disable(expectedModuleRequest);

      verify(folioTenantApiClient).disableLegacy(expectedModuleRequest);
      verify(moduleService).deleteModuleEntitlement(expectedModuleRequest);
    }

    @Test
    void positive_disableNotSupported() {
      when(moduleService.findAllModuleEntitlements(MODULE_ID, TENANT_ID)).thenReturn(MODULE_ENTITLEMENT);

      var moduleRequest = moduleRequest(legacyTenantInterfaceWithoutDisable());
      folioModuleService.disable(moduleRequest);

      verify(moduleService).deleteModuleEntitlement(moduleRequest);
      verifyNoInteractions(folioTenantApiClient);
    }

    private static InterfaceDescriptor legacyTenantInterface() {
      var routingEntries = List.of(
        new RoutingEntry().methods(List.of("POST", "DELETE")).pathPattern("/_/tenant"),
        new RoutingEntry().methods(List.of("POST")).pathPattern("/_/tenant/disable"));

      return new InterfaceDescriptor()
        .version("1.2")
        .id("_tenant")
        .interfaceType("system")
        .handlers(routingEntries);
    }

    private static InterfaceDescriptor legacyTenantInterfaceWithoutDisable() {
      var routingEntries = List.of(new RoutingEntry().methods(List.of("POST", "DELETE")).pathPattern("/_/tenant"));

      return new InterfaceDescriptor()
        .version("1.0")
        .id("_tenant")
        .interfaceType("system")
        .handlers(routingEntries);
    }
  }
}
