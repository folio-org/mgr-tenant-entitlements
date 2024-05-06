package org.folio.entitlement.integration.folio.flow;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestUtils.mockStageNames;
import static org.folio.entitlement.support.TestValues.moduleFlowParameters;
import static org.folio.entitlement.support.TestValues.moduleStageContext;
import static org.folio.entitlement.support.TestValues.singleThreadFlowEngine;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;

import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.IdentifiableStageContext;
import org.folio.entitlement.integration.folio.stage.FolioModuleEventPublisher;
import org.folio.entitlement.integration.folio.stage.FolioModuleUninstaller;
import org.folio.entitlement.integration.keycloak.KeycloakModuleResourceCleaner;
import org.folio.entitlement.integration.kong.KongModuleRouteCleaner;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.FlowEngine;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FolioModuleRevokeFlowFactoryTest {

  private final FlowEngine flowEngine = singleThreadFlowEngine("test-flow-engine", false);

  @InjectMocks private FolioModuleRevokeFlowFactory revokeFlowFactory;

  @Mock private FolioModuleUninstaller folioModuleUninstaller;
  @Mock private KongModuleRouteCleaner kongModuleRouteCleaner;
  @Mock private FolioModuleEventPublisher folioModuleEventPublisher;
  @Mock private KeycloakModuleResourceCleaner kcModuleResourceCleaner;

  @Test
  void createModuleFlow_positive_allConditionalStages() {
    mockStageNames(kongModuleRouteCleaner, kcModuleResourceCleaner, folioModuleUninstaller, folioModuleEventPublisher);
    revokeFlowFactory.setKongModuleRouteCleaner(kongModuleRouteCleaner);
    revokeFlowFactory.setKcModuleResourceCleaner(kcModuleResourceCleaner);

    var flowParameters = moduleFlowParameters(entitlementRequest(), moduleDescriptor());

    var flow = revokeFlowFactory.createModuleFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(flow);

    var inOrder = Mockito.inOrder(kongModuleRouteCleaner, kcModuleResourceCleaner,
      folioModuleUninstaller, folioModuleEventPublisher);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());
    verifyStageExecution(inOrder, folioModuleUninstaller, stageContext);
    verifyStageExecution(inOrder, folioModuleEventPublisher, stageContext);
    verifyStageExecution(inOrder, kongModuleRouteCleaner, stageContext);
    verifyStageExecution(inOrder, kcModuleResourceCleaner, stageContext);
  }

  @Test
  void createModuleFlow_positive_noConditionalStages() {
    mockStageNames(folioModuleUninstaller, folioModuleEventPublisher);

    var flowParameters = moduleFlowParameters(entitlementRequest(), moduleDescriptor());

    var flow = revokeFlowFactory.createModuleFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(flow);

    var inOrder = Mockito.inOrder(folioModuleUninstaller, folioModuleEventPublisher);

    var stageContext = moduleStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());
    verifyStageExecution(inOrder, folioModuleUninstaller, stageContext);
    verifyStageExecution(inOrder, folioModuleEventPublisher, stageContext);
  }

  @Test
  void createUiModuleFlow_positive_allConditionalStages() {
    var flowParameters = moduleFlowParameters(entitlementRequest(), UI_MODULE, moduleDescriptor());

    var flow = revokeFlowFactory.createUiModuleFlow(FLOW_STAGE_ID, IGNORE_ON_ERROR, flowParameters);
    flowEngine.execute(flow);

    Mockito.verifyNoInteractions(folioModuleUninstaller, folioModuleEventPublisher);
  }

  @Test
  void getEntitlementType_positive() {
    var result = revokeFlowFactory.getEntitlementType();
    assertThat(result).isEqualTo(REVOKE);
  }

  private static ModuleDescriptor moduleDescriptor() {
    return new ModuleDescriptor().id("mod-foo-1.0.0");
  }

  private static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder().type(REVOKE).tenantId(TENANT_ID).build();
  }

  private static <T extends IdentifiableStageContext> void verifyStageExecution(InOrder inOrder,
    DatabaseLoggingStage<T> stage, T context) {
    inOrder.verify(stage).onStart(context);
    inOrder.verify(stage).execute(context);
    inOrder.verify(stage).onSuccess(context);
  }
}
