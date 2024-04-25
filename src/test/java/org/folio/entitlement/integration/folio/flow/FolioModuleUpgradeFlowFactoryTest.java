package org.folio.entitlement.integration.folio.flow;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestValues.moduleFlowParameters;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;
import static org.mockito.Mockito.verifyNoInteractions;

import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.integration.keycloak.KeycloakModuleResourceUpdater;
import org.folio.entitlement.integration.kong.KongModuleRouteUpdater;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.entitlement.support.TestUtils;
import org.folio.flow.api.FlowEngine;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FolioModuleUpgradeFlowFactoryTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("test-flow-engine", false);

  @InjectMocks private FolioModuleUpgradeFlowFactory upgradeFlowFactory;
  @Mock private KongModuleRouteUpdater kongModuleRouteUpdater;
  @Mock private KeycloakModuleResourceUpdater kcModuleResourceUpdater;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void createModuleFlow_positive_allConditionalStages() {
    mockStageNames(kongModuleRouteUpdater, kcModuleResourceUpdater);
    upgradeFlowFactory.setKongModuleRouteUpdater(kongModuleRouteUpdater);
    upgradeFlowFactory.setKcModuleResourceUpdater(kcModuleResourceUpdater);

    var flowParameters = moduleFlowParameters(entitlementRequest(), moduleDescriptor());

    var flow = upgradeFlowFactory.createModuleFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(flow);

    var inOrder = Mockito.inOrder(kongModuleRouteUpdater, kcModuleResourceUpdater);
    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());
    verifyStageExecution(inOrder, kongModuleRouteUpdater, stageContext);
    verifyStageExecution(inOrder, kcModuleResourceUpdater, stageContext);
  }

  @Test
  void createModuleFlow_positive_noConditionalStages() {
    var flowParameters = moduleFlowParameters(entitlementRequest(), moduleDescriptor());
    upgradeFlowFactory.setKongModuleRouteUpdater(null);
    upgradeFlowFactory.setKcModuleResourceUpdater(null);

    var flow = upgradeFlowFactory.createModuleFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(flow);

    verifyNoInteractions(kongModuleRouteUpdater, kcModuleResourceUpdater);
  }

  @Test
  void createUiModuleFlow_positive() {
    var flowParameters = moduleFlowParameters(entitlementRequest(), moduleDescriptor(), UI_MODULE);
    var flow = upgradeFlowFactory.createUiModuleFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(flow);
    verifyNoInteractions(kongModuleRouteUpdater, kcModuleResourceUpdater);
  }

  @Test
  void getEntitlementType_positive() {
    var result = upgradeFlowFactory.getEntitlementType();
    assertThat(result).isEqualTo(UPGRADE);
  }

  private static ModuleDescriptor moduleDescriptor() {
    return new ModuleDescriptor().id("mod-foo-1.0.0");
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder().type(UPGRADE).tenantId(TENANT_ID).build();
  }

  private static <T extends IdentifiableStageContext> void verifyStageExecution(InOrder inOrder,
    DatabaseLoggingStage<T> stage, T context) {
    inOrder.verify(stage).onStart(context);
    inOrder.verify(stage).execute(context);
    inOrder.verify(stage).onSuccess(context);
  }
}
