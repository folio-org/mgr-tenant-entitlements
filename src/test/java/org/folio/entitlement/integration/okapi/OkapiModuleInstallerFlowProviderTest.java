package org.folio.entitlement.integration.okapi;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.entitlement.integration.folio.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.flow.model.FlowExecutionStrategy.IGNORE_ON_ERROR;

import java.util.Map;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.flow.api.Flow;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OkapiModuleInstallerFlowProviderTest {

  @InjectMocks private OkapiModuleInstallerFlowProvider okapiModuleInstallerFlowProvider;
  @Mock private OkapiModulesInstaller moduleInstaller;
  @Mock private ModulesEventPublisherStage modulesEventPublisherStage;

  @Test
  void prepareFlow_positive_entitleFlow() {
    var request = EntitlementRequest.builder().type(EntitlementType.ENTITLE).ignoreErrors(true).build();
    var context = StageContext.of(FLOW_STAGE_ID, Map.of(PARAM_REQUEST, request), emptyMap());

    var result = okapiModuleInstallerFlowProvider.prepareFlow(context);
    var expectedFlowId = FLOW_STAGE_ID + "/OkapiModuleEntitleFlow";
    var expectedFlow = Flow.builder()
      .id(expectedFlowId)
      .stage(moduleInstaller)
      .stage(modulesEventPublisherStage)
      .executionStrategy(IGNORE_ON_ERROR)
      .build();
    assertThat(result).isEqualTo(expectedFlow);
  }

  @Test
  void prepareFlow_positive_revokeFlow() {
    var request = EntitlementRequest.builder().type(EntitlementType.REVOKE).ignoreErrors(true).build();
    var context = StageContext.of(FLOW_STAGE_ID, Map.of(PARAM_REQUEST, request), emptyMap());

    var result = okapiModuleInstallerFlowProvider.prepareFlow(context);
    var expectedFlowId = FLOW_STAGE_ID + "/OkapiModuleRevokeFlow";
    var expectedFlow = Flow.builder()
      .id(expectedFlowId)
      .stage(moduleInstaller)
      .stage(modulesEventPublisherStage)
      .executionStrategy(IGNORE_ON_ERROR)
      .build();
    assertThat(result).isEqualTo(expectedFlow);
  }
}
