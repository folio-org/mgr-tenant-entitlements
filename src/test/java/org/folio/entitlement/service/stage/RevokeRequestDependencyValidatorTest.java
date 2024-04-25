package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RevokeRequestDependencyValidatorTest {

  @InjectMocks private RevokeRequestDependencyValidator dependencyValidator;
  @Mock private EntitlementCrudService entitlementCrudService;

  @Test
  void execute_positive_entitlementRevokeRequestWithZeroDependencies() {
    when(entitlementCrudService.findInstalledDependentEntitlements(APPLICATION_ID, TENANT_ID)).thenReturn(emptyList());

    var request = EntitlementRequest.builder().type(REVOKE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(request, applicationDescriptor());
    var stageContext = appStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    dependencyValidator.execute(stageContext);

    verify(entitlementCrudService).findInstalledDependentEntitlements(APPLICATION_ID, TENANT_ID);
  }

  @Test
  void execute_positive_entitlementRevokeRequest() {
    when(entitlementCrudService.findInstalledDependentEntitlements(APPLICATION_ID, TENANT_ID)).thenReturn(emptyList());

    var request = EntitlementRequest.builder().type(REVOKE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(request, applicationDescriptor());
    var stageContext = appStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    dependencyValidator.execute(stageContext);

    verify(entitlementCrudService).findInstalledDependentEntitlements(APPLICATION_ID, TENANT_ID);
  }

  @Test
  void execute_positive_entitlementRevokeRequestWithUnsatisfiedDependencies() {
    when(entitlementCrudService.findInstalledDependentEntitlements(APPLICATION_ID, TENANT_ID)).thenReturn(
      List.of(entitlement("app-foo-1.2.0"), entitlement("app-baz-4.2.1")));

    var request = EntitlementRequest.builder().type(REVOKE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(request, applicationDescriptor());
    var stageContext = appStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    assertThatThrownBy(() -> dependencyValidator.execute(stageContext))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("The following applications must be uninstalled first: [app-foo-1.2.0, app-baz-4.2.1]");

    verify(entitlementCrudService).findInstalledDependentEntitlements(APPLICATION_ID, TENANT_ID);
  }
}
