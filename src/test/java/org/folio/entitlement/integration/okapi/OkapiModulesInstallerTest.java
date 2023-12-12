package org.folio.entitlement.integration.okapi;

import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor.ActionType.ENABLE;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.simpleApplicationDescriptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.List;
import java.util.Map;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.okapi.model.TenantModuleDescriptor;
import org.folio.entitlement.service.EntitlementModuleService;
import org.folio.entitlement.support.TestConstants;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OkapiModulesInstallerTest {

  @InjectMocks private OkapiModulesInstaller okapiModulesInstaller;
  @Mock private OkapiClient okapiClient;
  @Mock private EntitlementModuleService moduleService;

  @Test
  void execute_positive() {
    var applicationDescriptor = simpleApplicationDescriptor(APPLICATION_ID);
    var request = EntitlementRequest.builder()
      .tenantId(TestConstants.TENANT_ID)
      .applications(List.of(APPLICATION_ID))
      .type(ENTITLE)
      .build();
    var context = StageContext.of("flowId", Map.of(PARAM_REQUEST, request),
      Map.of(PARAM_APP_DESCRIPTOR, applicationDescriptor, PARAM_TENANT_NAME, TENANT_NAME));
    okapiModulesInstaller.execute(context);

    var expectedPermission = List.of(new TenantModuleDescriptor().id("mod-bar-1.7.9").action(ENABLE));
    verify(okapiClient).installTenantModules(TENANT_NAME, false, false, null, false, false, expectedPermission, null);
    verify(moduleService).saveAll(TestConstants.TENANT_ID, APPLICATION_ID, List.of("mod-bar-1.7.9"));

    verifyNoMoreInteractions(okapiClient, moduleService);
  }

  @Test
  void execute_negative_okapiClientThrowsError() {
    var applicationDescriptor = simpleApplicationDescriptor(APPLICATION_ID);
    var request = EntitlementRequest.builder()
      .tenantId(TestConstants.TENANT_ID)
      .applications(List.of(APPLICATION_ID))
      .type(ENTITLE)
      .build();
    var context = StageContext.of("flowId", Map.of(PARAM_REQUEST, request),
      Map.of(PARAM_APP_DESCRIPTOR, applicationDescriptor, PARAM_TENANT_NAME, TENANT_NAME));

    doThrow(new RuntimeException("exception")).when(okapiClient)
      .installTenantModules(anyString(), anyBoolean(), anyBoolean(), any(), anyBoolean(), anyBoolean(), any(), any());

    assertThatThrownBy(() -> okapiModulesInstaller.execute(context))
      .isInstanceOf(RuntimeException.class)
      .hasMessage("exception");
  }
}
