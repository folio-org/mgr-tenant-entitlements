package org.folio.entitlement.integration.kong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.moduleFlowParameters;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.support.TestUtils;
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
class KongModuleRouteCreatorTest {

  @InjectMocks private KongModuleRouteCreator kongModuleRouteCreator;
  @Mock private KongGatewayService kongGatewayService;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    var moduleDescriptor = moduleDescriptor();
    var flowParameters = moduleFlowParameters(entitlementRequest(true), moduleDescriptor);
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    kongModuleRouteCreator.execute(stageContext);

    verify(kongGatewayService).addRoutes(TENANT_NAME, List.of(moduleDescriptor));
  }

  @Test
  void cancel_positive() {
    var moduleDescriptor = moduleDescriptor();
    var flowParameters = moduleFlowParameters(entitlementRequest(true), moduleDescriptor);
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    kongModuleRouteCreator.cancel(stageContext);

    verify(kongGatewayService).removeRoutes(TENANT_NAME, List.of(moduleDescriptor));
  }

  @Test
  void cancel_positive_purgeOnRollbackFalse() {
    var flowParameters = moduleFlowParameters(entitlementRequest(false), moduleDescriptor());
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    kongModuleRouteCreator.cancel(stageContext);

    verifyNoInteractions(kongGatewayService);
  }

  @Test
  void shouldCancelIfFailed_positive() {
    var flowParameters = moduleFlowParameters(entitlementRequest(false), moduleDescriptor());
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, stageParameters);

    var result = kongModuleRouteCreator.shouldCancelIfFailed(stageContext);

    assertThat(result).isTrue();
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
