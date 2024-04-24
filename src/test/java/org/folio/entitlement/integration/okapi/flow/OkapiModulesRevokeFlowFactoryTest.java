package org.folio.entitlement.integration.okapi.flow;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestUtils.verifyNoMoreInteractions;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;

import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.integration.keycloak.KeycloakAuthResourceCleaner;
import org.folio.entitlement.integration.kong.KongRouteCleaner;
import org.folio.entitlement.integration.okapi.stage.OkapiModulesEventPublisher;
import org.folio.entitlement.integration.okapi.stage.OkapiModulesInstaller;
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
class OkapiModulesRevokeFlowFactoryTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("test-flow-engine", false);

  @InjectMocks private OkapiModulesRevokeFlowFactory revokeFlowFactory;
  @Mock private KongRouteCleaner kongRouteCleaner;
  @Mock private OkapiModulesInstaller okapiModulesInstaller;
  @Mock private OkapiModulesEventPublisher okapiModulesEventPublisher;
  @Mock private KeycloakAuthResourceCleaner keycloakAuthResourceCleaner;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(this);
  }

  @Test
  void createFlow_positive_allConditionalStages() {
    mockStageNames(kongRouteCleaner, keycloakAuthResourceCleaner, okapiModulesEventPublisher, okapiModulesInstaller);
    revokeFlowFactory.setKongRouteCleaner(kongRouteCleaner);
    revokeFlowFactory.setKeycloakAuthResourceCleaner(keycloakAuthResourceCleaner);

    var flowParameters = TestValues.flowParameters(entitlementRequest(), applicationDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var flow = revokeFlowFactory.createFlow(stageContext);
    flowEngine.execute(flow);

    var inOrder = Mockito.inOrder(kongRouteCleaner, keycloakAuthResourceCleaner,
      okapiModulesEventPublisher, okapiModulesInstaller);

    var flowId = FLOW_STAGE_ID + "/OkapiModulesRevokeFlow";
    var expectedStageContext = appStageContext(flowId, emptyMap(), emptyMap());
    verifyStageExecution(inOrder, okapiModulesInstaller, expectedStageContext);
    verifyStageExecution(inOrder, okapiModulesEventPublisher, expectedStageContext);
    verifyStageExecution(inOrder, kongRouteCleaner, expectedStageContext);
    verifyStageExecution(inOrder, keycloakAuthResourceCleaner, expectedStageContext);
  }

  @Test
  void createFlow_positive_noConditionalStages() {
    mockStageNames(okapiModulesEventPublisher, okapiModulesInstaller);

    var flowParameters = TestValues.flowParameters(entitlementRequest(), applicationDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var flow = revokeFlowFactory.createFlow(stageContext);
    flowEngine.execute(flow);

    var inOrder = Mockito.inOrder(okapiModulesEventPublisher, okapiModulesInstaller);

    var flowId = FLOW_STAGE_ID + "/OkapiModulesRevokeFlow";
    var expectedStageContext = appStageContext(flowId, emptyMap(), emptyMap());
    verifyStageExecution(inOrder, okapiModulesInstaller, expectedStageContext);
    verifyStageExecution(inOrder, okapiModulesEventPublisher, expectedStageContext);
  }

  @Test
  void getEntitlementType_positive() {
    var result = revokeFlowFactory.getEntitlementType();
    assertThat(result).isEqualTo(REVOKE);
  }

  private static <T extends IdentifiableStageContext> void verifyStageExecution(InOrder inOrder,
    DatabaseLoggingStage<T> stage, T context) {
    inOrder.verify(stage).onStart(context);
    inOrder.verify(stage).execute(context);
    inOrder.verify(stage).onSuccess(context);
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder().type(REVOKE).tenantId(TENANT_ID).build();
  }
}
