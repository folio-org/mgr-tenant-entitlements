package org.folio.entitlement.integration.folio.flow;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestUtils.verifyNoMoreInteractions;
import static org.folio.entitlement.support.TestValues.moduleFlowParameters;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;

import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.integration.folio.stage.FolioModuleEventPublisher;
import org.folio.entitlement.integration.folio.stage.FolioModuleInstaller;
import org.folio.entitlement.integration.kafka.CapabilitiesModuleEventPublisher;
import org.folio.entitlement.integration.kafka.ScheduledJobModuleEventPublisher;
import org.folio.entitlement.integration.kafka.SystemUserModuleEventPublisher;
import org.folio.entitlement.integration.keycloak.KeycloakModuleResourceCreator;
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
class FolioModuleEntitleFlowFactoryTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("test-flow-engine", false);

  @InjectMocks private FolioModuleEntitleFlowFactory entitleFlowFactory;
  @Mock private FolioModuleEventPublisher folioModuleEventPublisher;
  @Mock private SystemUserModuleEventPublisher systemUserEventPublisher;
  @Mock private ScheduledJobModuleEventPublisher scheduledJobEventPublisher;
  @Mock private CapabilitiesModuleEventPublisher capabilitiesEventPublisher;

  @Mock private FolioModuleInstaller folioModuleInstaller;
  @Mock private KeycloakModuleResourceCreator kcModuleResourceCreator;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(this);
  }

  @Test
  void createModuleFlow_positive_allConditionalStages() {
    mockStageNames(kcModuleResourceCreator, folioModuleEventPublisher,
      folioModuleInstaller, systemUserEventPublisher, scheduledJobEventPublisher, capabilitiesEventPublisher);
    entitleFlowFactory.setKcModuleResourceCreator(kcModuleResourceCreator);

    var flowParameters = moduleFlowParameters(entitlementRequest(), moduleDescriptor());

    var flow = entitleFlowFactory.createModuleFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(flow);

    var inOrder = Mockito.inOrder(kcModuleResourceCreator, folioModuleEventPublisher,
      systemUserEventPublisher, scheduledJobEventPublisher, capabilitiesEventPublisher, folioModuleInstaller);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());
    verifyStageExecution(inOrder, kcModuleResourceCreator, stageContext);
    verifyStageExecution(inOrder, systemUserEventPublisher, stageContext);
    verifyStageExecution(inOrder, folioModuleInstaller, stageContext);
    verifyStageExecution(inOrder, folioModuleEventPublisher, stageContext);
    verifyStageExecution(inOrder, scheduledJobEventPublisher, stageContext);
    verifyStageExecution(inOrder, capabilitiesEventPublisher, stageContext);
  }

  @Test
  void createModuleFlow_positive_noConditionalStages() {
    mockStageNames(folioModuleEventPublisher, folioModuleInstaller,
      systemUserEventPublisher, scheduledJobEventPublisher, capabilitiesEventPublisher);

    var flowParameters = moduleFlowParameters(entitlementRequest(), moduleDescriptor());

    var flow = entitleFlowFactory.createModuleFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(flow);

    var inOrder = Mockito.inOrder(folioModuleEventPublisher, systemUserEventPublisher,
      scheduledJobEventPublisher, capabilitiesEventPublisher, folioModuleInstaller);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());
    verifyStageExecution(inOrder, systemUserEventPublisher, stageContext);
    verifyStageExecution(inOrder, folioModuleInstaller, stageContext);
    verifyStageExecution(inOrder, folioModuleEventPublisher, stageContext);
    verifyStageExecution(inOrder, scheduledJobEventPublisher, stageContext);
    verifyStageExecution(inOrder, capabilitiesEventPublisher, stageContext);
  }

  @Test
  void createUiModuleFlow_positive_allConditionalStages() {
    mockStageNames(capabilitiesEventPublisher);
    var flowParameters = moduleFlowParameters(entitlementRequest(), UI_MODULE, moduleDescriptor());

    var flow = entitleFlowFactory.createUiModuleFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(flow);

    var inOrder = Mockito.inOrder(capabilitiesEventPublisher);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());
    verifyStageExecution(inOrder, capabilitiesEventPublisher, stageContext);
  }

  @Test
  void getEntitlementType_positive() {
    var result = entitleFlowFactory.getEntitlementType();
    assertThat(result).isEqualTo(ENTITLE);
  }

  private static ModuleDescriptor moduleDescriptor() {
    return new ModuleDescriptor().id("mod-foo-1.0.0");
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
  }

  private static <T extends IdentifiableStageContext> void verifyStageExecution(InOrder inOrder,
    DatabaseLoggingStage<T> stage, T context) {
    inOrder.verify(stage).onStart(context);
    inOrder.verify(stage).execute(context);
    inOrder.verify(stage).onSuccess(context);
  }
}
