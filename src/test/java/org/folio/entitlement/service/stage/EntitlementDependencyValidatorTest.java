package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FAILED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.applicationDependency;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.service.flow.EntitlementFlowService;
import org.folio.entitlement.support.TestValues;
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
class EntitlementDependencyValidatorTest {

  @InjectMocks private EntitlementDependencyValidator entitlementDependencyValidator;
  @Mock private ApplicationManagerService applicationManagerService;
  @Mock private EntitlementFlowService entitlementFlowService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(applicationManagerService, entitlementFlowService);
  }

  @Test
  void execute_positive_entitlementRequest() {
    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_APP_ID, APPLICATION_ID);
    var stageContext = StageContext.of(FLOW_ID, flowParameters, Map.of(PARAM_APP_DESCRIPTOR, applicationDescriptor()));
    var dependencyIds = List.of("app-foo-1.2.0", "app-bar-2.3.9", "app-baz-4.2.1");
    var dependencyNames = Set.of("app-foo", "app-bar", "app-baz");
    var expectedEntitlements = mapItems(dependencyIds, applicationId -> flow(applicationId, ENTITLE, FINISHED));
    when(entitlementFlowService.findLastFlowsByAppNames(dependencyNames, TENANT_ID)).thenReturn(expectedEntitlements);

    entitlementDependencyValidator.execute(stageContext);
  }

  @Test
  void execute_positive_entitlementRequestWithZeroDependencies() {
    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_APP_ID, APPLICATION_ID);
    var applicationDescriptor = TestValues.applicationDescriptor();
    var stageContext = StageContext.of(FLOW_ID, flowParameters, Map.of(PARAM_APP_DESCRIPTOR, applicationDescriptor));

    entitlementDependencyValidator.execute(stageContext);

    verifyNoInteractions(applicationManagerService);
  }

  @Test
  void execute_negative_entitlementRequestWithUnsatisfiedDependencies() {
    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_APP_ID, APPLICATION_ID);
    var applicationDescriptor = applicationDescriptor();
    var stageContext = StageContext.of(FLOW_ID, flowParameters, Map.of(PARAM_APP_DESCRIPTOR, applicationDescriptor));
    var dependencyNames = Set.of("app-foo", "app-bar", "app-baz");
    var expectedEntitlements = List.of(
      flow("app-foo-1.2.0", ENTITLE, FAILED),
      flow("app-foo-1.2.0", REVOKE, FINISHED));

    when(entitlementFlowService.findLastFlowsByAppNames(dependencyNames, TENANT_ID)).thenReturn(expectedEntitlements);

    assertThatThrownBy(() -> entitlementDependencyValidator.execute(stageContext))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("The following application dependencies must be installed first: "
        + "app-bar 2.3.9, app-baz 4.2.1, app-foo 1.2.0");
  }

  @Test
  void execute_positive_entitlementRevokeRequest() {
    var request = EntitlementRequest.builder().type(REVOKE).tenantId(TENANT_ID).build();
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_APP_ID, APPLICATION_ID);
    var applicationDescriptor = applicationDescriptor();
    var stageContext = StageContext.of(FLOW_ID, flowParameters, Map.of(PARAM_APP_DESCRIPTOR, applicationDescriptor));
    when(entitlementFlowService.findLastDependentFlows(APPLICATION_ID, TENANT_ID)).thenReturn(emptyList());

    entitlementDependencyValidator.execute(stageContext);

    verify(entitlementFlowService).findLastDependentFlows(APPLICATION_ID, TENANT_ID);
  }

  @Test
  void execute_positive_entitlementRevokeRequestWithZeroDependencies() {
    var request = EntitlementRequest.builder().type(REVOKE).tenantId(TENANT_ID).build();
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_APP_ID, APPLICATION_ID);
    var applicationDescriptor = TestValues.applicationDescriptor();
    var stageContext = StageContext.of(FLOW_ID, flowParameters, Map.of(PARAM_APP_DESCRIPTOR, applicationDescriptor));
    when(entitlementFlowService.findLastDependentFlows(APPLICATION_ID, TENANT_ID)).thenReturn(emptyList());

    entitlementDependencyValidator.execute(stageContext);

    verify(entitlementFlowService).findLastDependentFlows(APPLICATION_ID, TENANT_ID);
  }

  @Test
  void execute_positive_entitlementRevokeRequestWithUnsatisfiedDependencies() {
    var request = EntitlementRequest.builder().type(REVOKE).tenantId(TENANT_ID).build();
    var flowParameters = Map.of(PARAM_REQUEST, request, PARAM_APP_ID, APPLICATION_ID);
    var applicationDescriptor = applicationDescriptor();
    var stageContext = StageContext.of(FLOW_ID, flowParameters, Map.of(PARAM_APP_DESCRIPTOR, applicationDescriptor));
    when(entitlementFlowService.findLastDependentFlows(APPLICATION_ID, TENANT_ID)).thenReturn(
      List.of(flow("app-foo-1.2.0", ENTITLE, FINISHED), flow("app-baz-4.2.1", ENTITLE, FINISHED)));

    assertThatThrownBy(() -> entitlementDependencyValidator.execute(stageContext))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("The following applications must be uninstalled first: [app-foo-1.2.0, app-baz-4.2.1]");

    verify(entitlementFlowService).findLastDependentFlows(APPLICATION_ID, TENANT_ID);
  }

  private static ApplicationDescriptor applicationDescriptor() {
    return new ApplicationDescriptor()
      .id(APPLICATION_ID)
      .dependencies(List.of(
        applicationDependency("app-foo", "1.2.0"),
        applicationDependency("app-bar", "2.3.9"),
        applicationDependency("app-baz", "4.2.1")));
  }

  private static ApplicationFlow flow(String appId, EntitlementType type, ExecutionStatus status) {
    return new ApplicationFlow().applicationId(appId).tenantId(TENANT_ID).type(type).status(status);
  }
}
