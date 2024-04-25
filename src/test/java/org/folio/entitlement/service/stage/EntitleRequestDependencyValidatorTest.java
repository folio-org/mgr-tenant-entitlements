package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FAILED;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.applicationDependency;
import static org.folio.entitlement.support.TestValues.flowParameters;
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
import org.folio.entitlement.service.flow.ApplicationFlowService;
import org.folio.entitlement.support.TestValues;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitleRequestDependencyValidatorTest {

  @InjectMocks private EntitleRequestDependencyValidator dependencyValidator;
  @Mock private ApplicationManagerService applicationManagerService;
  @Mock private ApplicationFlowService applicationFlowService;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(applicationManagerService, applicationFlowService);
  }

  @Test
  void execute_positive() {
    var dependencyIds = List.of("app-foo-1.2.0", "app-bar-2.3.9", "app-baz-4.2.1");
    var dependencyNames = Set.of("app-foo", "app-bar", "app-baz");
    var expectedEntitlements = mapItems(dependencyIds, applicationId -> flow(applicationId, ENTITLE, FINISHED));
    when(applicationFlowService.findLastFlowsByNames(dependencyNames, TENANT_ID)).thenReturn(expectedEntitlements);

    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(request, applicationDescriptor());
    var stageContext = appStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    dependencyValidator.execute(stageContext);

    verify(applicationFlowService).findLastFlowsByNames(dependencyNames, TENANT_ID);
  }

  @Test
  void execute_positive_zeroDependencies() {
    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(request, TestValues.applicationDescriptor());
    var stageContext = appStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    dependencyValidator.execute(stageContext);

    verifyNoInteractions(applicationManagerService);
  }

  @Test
  void execute_negative_unsatisfiedDependencies() {
    var dependencyNames = Set.of("app-foo", "app-bar", "app-baz");
    var expectedEntitlements = List.of(flow("app-foo-1.2.0", ENTITLE, FAILED), flow("app-foo-1.2.0", REVOKE, FINISHED));
    when(applicationFlowService.findLastFlowsByNames(dependencyNames, TENANT_ID)).thenReturn(expectedEntitlements);

    var request = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(request, applicationDescriptor());
    var stageContext = appStageContext(FLOW_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    assertThatThrownBy(() -> dependencyValidator.execute(stageContext))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("The following application dependencies must be installed first: "
        + "app-bar 2.3.9, app-baz 4.2.1, app-foo 1.2.0");
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
