package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyMap;
import static org.folio.entitlement.integration.folio.ApplicationStageContext.PARAM_APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.integration.folio.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.integration.folio.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.dependency;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.Dependency;
import org.folio.entitlement.integration.folio.ApplicationStageContext;
import org.folio.entitlement.service.ApplicationDependencyService;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationDependencySaverTest {

  private static final Dependency DEPENDENCY = dependency("dep-1.0.0");

  @InjectMocks private ApplicationDependencySaver applicationDependencySaver;
  @Mock private ApplicationDependencyService applicationDependencyService;

  @ParameterizedTest
  @EnumSource(EntitlementType.class)
  void execute_positive(EntitlementType type) {
    var stageContext = stageContext(type);
    applicationDependencySaver.execute(stageContext);
    verify(applicationDependencyService).saveEntitlementDependencies(TENANT_ID, APPLICATION_ID, List.of(DEPENDENCY));
  }

  @ParameterizedTest
  @EnumSource(EntitlementType.class)
  void cancel_positive(EntitlementType type) {
    var stageContext = stageContext(type);
    applicationDependencySaver.cancel(stageContext);
    verify(applicationDependencyService).deleteEntitlementDependencies(TENANT_ID, APPLICATION_ID, List.of(DEPENDENCY));
  }

  private static ApplicationStageContext stageContext(EntitlementType type) {
    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(type).authToken(OKAPI_TOKEN).build();
    var flowParameters = Map.of(
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_REQUEST, request,
      PARAM_APPLICATION_DESCRIPTOR, applicationDescriptor(APPLICATION_ID, DEPENDENCY));

    return appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());
  }
}
