package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyMap;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.appDescriptor;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.dependency;
import static org.mockito.Mockito.doNothing;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.Dependency;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
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
class ApplicationDependencyCleanerTest {

  private static final Dependency DEPENDENCY = dependency("dep-1.0.0");

  @InjectMocks private ApplicationDependencyCleaner applicationDependencyCleaner;
  @Mock private ApplicationDependencyService applicationDependencyService;

  @ParameterizedTest
  @EnumSource(EntitlementType.class)
  void execute_positive(EntitlementType type) {
    var stageContext = stageContext(type);

    doNothing().when(applicationDependencyService)
      .deleteEntitlementDependencies(TENANT_ID, APPLICATION_ID, List.of(DEPENDENCY));

    applicationDependencyCleaner.execute(stageContext);
  }

  @ParameterizedTest
  @EnumSource(EntitlementType.class)
  void cancel_positive(EntitlementType type) {
    var stageContext = stageContext(type);

    doNothing().when(applicationDependencyService)
      .saveEntitlementDependencies(TENANT_ID, APPLICATION_ID, List.of(DEPENDENCY));

    applicationDependencyCleaner.cancel(stageContext);
  }

  private static ApplicationStageContext stageContext(EntitlementType type) {
    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(type).okapiToken(OKAPI_TOKEN).build();
    var flowParameters = Map.of(
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_REQUEST, request,
      PARAM_APPLICATION_DESCRIPTOR, appDescriptor(APPLICATION_ID, DEPENDENCY));

    return appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());
  }
}
