package org.folio.entitlement.integration.okapi;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.okapi.flow.OkapiModulesEntitleFlowFactory;
import org.folio.entitlement.integration.okapi.flow.OkapiModulesFlowProvider;
import org.folio.entitlement.integration.okapi.flow.OkapiModulesRevokeFlowFactory;
import org.folio.entitlement.integration.okapi.flow.OkapiModulesUpgradeFlowFactory;
import org.folio.flow.api.Flow;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OkapiModulesFlowProviderTest {

  private OkapiModulesFlowProvider okapiModulesFlowProvider;

  @Mock private OkapiModulesRevokeFlowFactory installModulesFlowFactory;
  @Mock private OkapiModulesEntitleFlowFactory uninstallModulesFlowFactory;
  @Mock private OkapiModulesUpgradeFlowFactory upgradeModulesFlowFactory;

  @BeforeEach
  void setUp() {
    when(installModulesFlowFactory.getEntitlementType()).thenReturn(ENTITLE);
    when(uninstallModulesFlowFactory.getEntitlementType()).thenReturn(REVOKE);
    when(upgradeModulesFlowFactory.getEntitlementType()).thenReturn(UPGRADE);

    okapiModulesFlowProvider = new OkapiModulesFlowProvider(
      List.of(installModulesFlowFactory, uninstallModulesFlowFactory, upgradeModulesFlowFactory));
  }

  @Test
  void createFlow_positive_entitleFlow() {
    var request = EntitlementRequest.builder().type(ENTITLE).ignoreErrors(true).build();
    var context = StageContext.of(FLOW_STAGE_ID, Map.of(PARAM_REQUEST, request), emptyMap());
    var appContext = ApplicationStageContext.decorate(context);

    var expectedFlowId = FLOW_STAGE_ID + "/OkapiModuleEntitleFlow";
    var expectedFlow = Flow.builder().id(expectedFlowId).executionStrategy(IGNORE_ON_ERROR).build();

    when(installModulesFlowFactory.createFlow(appContext)).thenReturn(expectedFlow);
    var result = okapiModulesFlowProvider.createFlow(context);

    assertThat(result).isEqualTo(expectedFlow);
  }

  @Test
  void createFlow_positive_revokeFlow() {
    var request = EntitlementRequest.builder().type(REVOKE).ignoreErrors(true).build();
    var context = StageContext.of(FLOW_STAGE_ID, Map.of(PARAM_REQUEST, request), emptyMap());
    var appContext = ApplicationStageContext.decorate(context);

    var expectedFlowId = FLOW_STAGE_ID + "/OkapiModuleEntitleFlow";
    var expectedFlow = Flow.builder().id(expectedFlowId).executionStrategy(IGNORE_ON_ERROR).build();

    when(uninstallModulesFlowFactory.createFlow(appContext)).thenReturn(expectedFlow);
    var result = okapiModulesFlowProvider.createFlow(context);

    assertThat(result).isEqualTo(expectedFlow);
  }

  @Test
  void createFlow_positive_upgradeFlow() {
    var request = EntitlementRequest.builder().type(UPGRADE).ignoreErrors(true).build();
    var context = StageContext.of(FLOW_STAGE_ID, Map.of(PARAM_REQUEST, request), emptyMap());
    var appContext = ApplicationStageContext.decorate(context);

    var expectedFlowId = FLOW_STAGE_ID + "/OkapiModuleEntitleFlow";
    var expectedFlow = Flow.builder().id(expectedFlowId).executionStrategy(IGNORE_ON_ERROR).build();

    when(upgradeModulesFlowFactory.createFlow(appContext)).thenReturn(expectedFlow);
    var result = okapiModulesFlowProvider.createFlow(context);

    assertThat(result).isEqualTo(expectedFlow);
  }
}
