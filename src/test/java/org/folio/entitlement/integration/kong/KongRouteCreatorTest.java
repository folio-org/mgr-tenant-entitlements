package org.folio.entitlement.integration.kong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.support.TestUtils;
import org.folio.entitlement.support.TestValues;
import org.folio.test.types.UnitTest;
import org.folio.tools.kong.service.KongGatewayService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KongRouteCreatorTest {

  @InjectMocks private KongRouteCreator kongRouteCreator;
  @Mock private KongGatewayService kongGatewayService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    var moduleDescriptor = moduleDescriptor();
    var applicationDescriptor = applicationDescriptor(moduleDescriptor);
    var flowParameters = flowParameters(entitlementRequest(true), applicationDescriptor);
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    kongRouteCreator.execute(stageContext);

    verify(kongGatewayService).addRoutes(TENANT_NAME, List.of(moduleDescriptor));
  }

  @Test
  void cancel_positive() {
    var moduleDescriptor = moduleDescriptor();
    var applicationDescriptor = applicationDescriptor(moduleDescriptor);
    var flowParameters = flowParameters(entitlementRequest(true), applicationDescriptor);
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    kongRouteCreator.cancel(stageContext);

    verify(kongGatewayService).removeRoutes(TENANT_NAME, List.of(moduleDescriptor));
  }

  @Test
  void cancel_positive_purgeOnRollbackFalse() {
    var flowParameters = flowParameters(entitlementRequest(false), applicationDescriptor(moduleDescriptor()));
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    kongRouteCreator.cancel(stageContext);

    verifyNoInteractions(kongGatewayService);
  }

  @Test
  void shouldCancelIfFailed_positive() {
    var moduleDescriptor = moduleDescriptor();
    var flowParameters = flowParameters(entitlementRequest(false), applicationDescriptor(moduleDescriptor));
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var result = kongRouteCreator.shouldCancelIfFailed(stageContext);

    assertThat(result).isTrue();
  }

  private static ApplicationDescriptor applicationDescriptor(ModuleDescriptor... moduleDescriptors) {
    return TestValues.applicationDescriptor().moduleDescriptors(List.of(moduleDescriptors));
  }

  private static EntitlementRequest entitlementRequest(boolean purgeOnRollback) {
    return EntitlementRequest.builder()
      .type(ENTITLE)
      .tenantId(TENANT_ID)
      .applications(List.of(APPLICATION_ID))
      .purgeOnRollback(purgeOnRollback)
      .build();
  }

  private static ModuleDescriptor moduleDescriptor() {
    return new ModuleDescriptor().id("mod-foo-1.0.0");
  }
}
