package org.folio.entitlement.integration.okapi.flow;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestUtils.verifyNoMoreInteractions;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.folio.entitlement.support.TestValues.okapiStageContext;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;

import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.integration.kafka.CapabilitiesEventPublisher;
import org.folio.entitlement.integration.kafka.ScheduledJobEventPublisher;
import org.folio.entitlement.integration.kafka.SystemUserEventPublisher;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCreator;
import org.folio.entitlement.integration.okapi.stage.OkapiModulesEventPublisher;
import org.folio.entitlement.integration.okapi.stage.OkapiModulesInstaller;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
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
class OkapiModulesEntitleFlowFactoryTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("test-flow-engine", false);

  @InjectMocks private OkapiModulesEntitleFlowFactory entitleFlowFactory;

  @Mock private SystemUserEventPublisher systemUserEventPublisher;
  @Mock private ScheduledJobEventPublisher scheduledJobEventPublisher;
  @Mock private CapabilitiesEventPublisher capabilitiesEventPublisher;
  @Mock private OkapiModulesEventPublisher okapiModulesEventPublisher;

  @Mock private OkapiModulesInstaller okapiModulesInstaller;
  @Mock private KeycloakAuthResourceCreator keycloakAuthResourceCreator;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(this);
  }

  @Test
  void createFlow_positive_allConditionalStages() {
    mockStageNames(keycloakAuthResourceCreator, okapiModulesEventPublisher,
      systemUserEventPublisher, scheduledJobEventPublisher, capabilitiesEventPublisher, okapiModulesInstaller);
    entitleFlowFactory.setKeycloakAuthResourceCreator(keycloakAuthResourceCreator);

    var flowParameters = flowParameters(entitlementRequest(), applicationDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var flow = entitleFlowFactory.createFlow(stageContext, emptyMap());
    flowEngine.execute(flow);

    var inOrder = Mockito.inOrder(keycloakAuthResourceCreator, okapiModulesEventPublisher,
      systemUserEventPublisher, scheduledJobEventPublisher, capabilitiesEventPublisher, okapiModulesInstaller);

    var flowId = FLOW_STAGE_ID + "/OkapiModulesEntitleFlow";
    var okapiStageContext = okapiStageContext(flowId, emptyMap(), emptyMap());
    verifyStageExecution(inOrder, keycloakAuthResourceCreator, okapiStageContext);
    verifyStageExecution(inOrder, systemUserEventPublisher, okapiStageContext);
    verifyStageExecution(inOrder, okapiModulesInstaller, okapiStageContext);

    var expectedStageContext = appStageContext(flowId, emptyMap(), emptyMap());
    verifyStageExecution(inOrder, scheduledJobEventPublisher, okapiStageContext);
    verifyStageExecution(inOrder, capabilitiesEventPublisher, okapiStageContext);
    verifyStageExecution(inOrder, okapiModulesEventPublisher, okapiStageContext);
  }

  @Test
  void createFlow_positive_noConditionalStages() {
    mockStageNames(okapiModulesEventPublisher, systemUserEventPublisher, scheduledJobEventPublisher,
      capabilitiesEventPublisher, okapiModulesInstaller);

    var flowParameters = flowParameters(entitlementRequest(), applicationDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var flow = entitleFlowFactory.createFlow(stageContext, emptyMap());
    flowEngine.execute(flow);

    var inOrder = Mockito.inOrder(okapiModulesEventPublisher, systemUserEventPublisher,
      scheduledJobEventPublisher, capabilitiesEventPublisher, okapiModulesInstaller);

    var flowId = FLOW_STAGE_ID + "/OkapiModulesEntitleFlow";
    var expectedStageContext = appStageContext(flowId, emptyMap(), emptyMap());
    var okapiStageContext = okapiStageContext(flowId, emptyMap(), emptyMap());
    verifyStageExecution(inOrder, systemUserEventPublisher, okapiStageContext);
    verifyStageExecution(inOrder, okapiModulesInstaller, okapiStageContext);
    verifyStageExecution(inOrder, scheduledJobEventPublisher, okapiStageContext);
    verifyStageExecution(inOrder, capabilitiesEventPublisher, okapiStageContext);
    verifyStageExecution(inOrder, okapiModulesEventPublisher, okapiStageContext);
  }

  @Test
  void getEntitlementType_positive() {
    var result = entitleFlowFactory.getEntitlementType();
    assertThat(result).isEqualTo(ENTITLE);
  }

  private static <T extends IdentifiableStageContext> void verifyStageExecution(InOrder inOrder,
    DatabaseLoggingStage<T> stage, T context) {
    inOrder.verify(stage).onStart(context);
    inOrder.verify(stage).execute(context);
    inOrder.verify(stage).onSuccess(context);
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
  }
}
