package org.folio.entitlement.integration.folio;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_DISCOVERY;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_MODULE_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.mockito.Mockito.verify;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.folio.model.ModuleRequest;
import org.folio.entitlement.support.TestUtils;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FolioModuleInstallerTest {

  private static final String MODULE_ID = "mod-foo-1.0.0";
  private static final String MODULE_URL = "http://mod-foo:8081";

  @InjectMocks private FolioModuleInstaller moduleInstaller;
  @Mock private FolioModuleService folioModuleService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    var request = EntitlementRequest.builder()
      .type(ENTITLE).tenantParameters("loadSamples=true").tenantId(TENANT_ID).build();
    var tenantInterface = tenantInterface();
    var flowParameters = flowParameters(moduleDescriptor(tenantInterface), request);
    var context = StageContext.of(FLOW_ID, flowParameters, contextData());

    moduleInstaller.execute(context);

    var expectedParameters = List.of(new Parameter().key("loadSamples").value("true"));
    var expectedModuleRequest = moduleRequest(expectedParameters, tenantInterface);
    verify(folioModuleService).enable(expectedModuleRequest);
  }

  @Test
  void execute_positive_installModuleWithoutTenantApi() {
    var request = EntitlementRequest.builder()
      .type(ENTITLE).tenantParameters("loadSamples=true").tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(moduleDescriptor(), request);
    var context = StageContext.of(FLOW_ID, flowParameters, contextData());

    moduleInstaller.execute(context);

    var expectedParameters = List.of(new Parameter().key("loadSamples").value("true"));
    verify(folioModuleService).enable(moduleRequest(expectedParameters, null));
  }

  @Test
  void cancel_positive_entitleRequest() {
    var request = EntitlementRequest.builder()
      .type(ENTITLE)
      .tenantParameters("loadSamples=true")
      .purgeOnRollback(true)
      .tenantId(TENANT_ID)
      .build();

    var tenantApi = tenantInterface();
    var flowParameters = flowParameters(moduleDescriptor(tenantApi), request);
    var context = StageContext.of(FLOW_ID, flowParameters, contextData());

    moduleInstaller.cancel(context);

    var expectedParameters = List.of(new Parameter().key("loadSamples").value("true"));
    verify(folioModuleService).disable(moduleRequest(expectedParameters, tenantApi, true));
  }

  @Test
  void getStageName_positive() {
    var flowParameters = Map.of(PARAM_MODULE_ID, MODULE_ID);
    var context = StageContext.of(FLOW_ID, flowParameters, emptyMap());

    var stageName = moduleInstaller.getStageName(context);

    assertThat(stageName).isEqualTo(MODULE_ID + "-moduleInstaller");
  }

  private static ModuleDescriptor moduleDescriptor(InterfaceDescriptor... interfaceDescriptors) {
    return new ModuleDescriptor().id(MODULE_ID).provides(Arrays.asList(interfaceDescriptors));
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

  private static Map<String, Object> flowParameters(ModuleDescriptor moduleDescriptor, EntitlementRequest request) {
    return Map.of(
      PARAM_APP_ID, APPLICATION_ID,
      PARAM_MODULE_ID, MODULE_ID,
      PARAM_MODULE_DISCOVERY, MODULE_URL,
      PARAM_MODULE_DESCRIPTOR, moduleDescriptor,
      PARAM_REQUEST, request);
  }

  private static Map<String, Object> contextData() {
    return Map.of(PARAM_TENANT_NAME, TENANT_NAME);
  }

  private static ModuleRequest moduleRequest(List<Parameter> parameters, InterfaceDescriptor tenantApi) {
    return moduleRequest(parameters, tenantApi, false);
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
}
