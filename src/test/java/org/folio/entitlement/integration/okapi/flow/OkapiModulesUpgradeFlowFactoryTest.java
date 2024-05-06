package org.folio.entitlement.integration.okapi.flow;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestUtils.verifyNoMoreInteractions;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.okapiStageContext;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.mockito.Mockito.verifyNoInteractions;

import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.integration.kafka.CapabilitiesEventPublisher;
import org.folio.entitlement.integration.kafka.ScheduledJobEventPublisher;
import org.folio.entitlement.integration.kafka.SystemUserEventPublisher;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceUpdater;
import org.folio.entitlement.integration.kong.KongRouteUpdater;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.entitlement.support.TestValues;
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
class OkapiModulesUpgradeFlowFactoryTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("test-flow-engine", false);

  @InjectMocks private OkapiModulesUpgradeFlowFactory upgradeFlowFactory;
  @Mock private KongRouteUpdater kongRouteUpdater;
  @Mock private KeycloakAuthResourceUpdater keycloakAuthResourceUpdater;
  @Mock private SystemUserEventPublisher systemUserEventPublisher;
  @Mock private ScheduledJobEventPublisher scheduledJobEventPublisher;
  @Mock private CapabilitiesEventPublisher capabilitiesEventPublisher;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(this);
  }

  @Test
  void createFlow_positive_allConditionalStages() {
    mockStageNames(kongRouteUpdater, keycloakAuthResourceUpdater,
      scheduledJobEventPublisher, capabilitiesEventPublisher, systemUserEventPublisher);
    upgradeFlowFactory.setKongRouteUpdater(kongRouteUpdater);
    upgradeFlowFactory.setKeycloakAuthResourceUpdater(keycloakAuthResourceUpdater);

    var flowParameters = TestValues.flowParameters(entitlementRequest(), applicationDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var flow = upgradeFlowFactory.createFlow(stageContext, emptyMap());
    flowEngine.execute(flow);

    var inOrder = Mockito.inOrder(kongRouteUpdater, keycloakAuthResourceUpdater,
      scheduledJobEventPublisher, capabilitiesEventPublisher, systemUserEventPublisher);

    var flowId = FLOW_STAGE_ID + "/OkapiModulesUpgradeFlow";
    var okapiStageContext = okapiStageContext(flowId, emptyMap(), emptyMap());
    verifyStageExecution(inOrder, kongRouteUpdater, okapiStageContext);
    verifyStageExecution(inOrder, keycloakAuthResourceUpdater, okapiStageContext);
    verifyStageExecution(inOrder, systemUserEventPublisher, okapiStageContext);
    verifyStageExecution(inOrder, scheduledJobEventPublisher, okapiStageContext);
    verifyStageExecution(inOrder, capabilitiesEventPublisher, okapiStageContext);
  }

  @Test
  void createFlow_positive_noConditionalStages() {
    mockStageNames(scheduledJobEventPublisher, capabilitiesEventPublisher, systemUserEventPublisher);
    var flowParameters = TestValues.flowParameters(entitlementRequest(), applicationDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var flow = upgradeFlowFactory.createFlow(stageContext, emptyMap());
    flowEngine.execute(flow);

    var inOrder = Mockito.inOrder(scheduledJobEventPublisher, capabilitiesEventPublisher, systemUserEventPublisher);
    var flowId = FLOW_STAGE_ID + "/OkapiModulesUpgradeFlow";
    var okapiStageContext = okapiStageContext(flowId, emptyMap(), emptyMap());
    verifyStageExecution(inOrder, systemUserEventPublisher, okapiStageContext);
    verifyStageExecution(inOrder, scheduledJobEventPublisher, okapiStageContext);
    verifyStageExecution(inOrder, capabilitiesEventPublisher, okapiStageContext);

    verifyNoInteractions(keycloakAuthResourceUpdater, kongRouteUpdater);
  }

  @Test
  void getEntitlementType_positive() {
    var result = upgradeFlowFactory.getEntitlementType();
    assertThat(result).isEqualTo(UPGRADE);
  }

  private static <T extends IdentifiableStageContext> void verifyStageExecution(InOrder inOrder,
    DatabaseLoggingStage<T> stage, T context) {
    inOrder.verify(stage).onStart(context);
    inOrder.verify(stage).execute(context);
    inOrder.verify(stage).onSuccess(context);
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder().type(UPGRADE).tenantId(TENANT_ID).build();
  }
}
