package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.commonStageContext;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationFlowQueuingStageTest {

  @InjectMocks private ApplicationFlowQueuingStage applicationFlowQueuingStage;
  @Mock private ApplicationFlowService applicationFlowService;

  @Test
  void execute_positive() {
    var applicationFlow = applicationFlow();
    var request = entitlementRequest();
    when(applicationFlowService.createQueuedApplicationFlows(FLOW_ID, request)).thenReturn(List.of(applicationFlow));

    var flowParameters = Map.of(PARAM_REQUEST, request);
    var stageContext = commonStageContext(FLOW_ID, flowParameters, emptyMap());

    applicationFlowQueuingStage.execute(stageContext);

    assertThat(stageContext.getQueuedApplicationFlows()).isEqualTo(Map.of(APPLICATION_ID, APPLICATION_FLOW_ID));
  }

  static ApplicationFlow applicationFlow() {
    return new ApplicationFlow()
      .id(APPLICATION_FLOW_ID)
      .flowId(FLOW_ID)
      .applicationId(APPLICATION_ID)
      .tenantId(TENANT_ID)
      .status(ExecutionStatus.QUEUED)
      .type(ENTITLE);
  }

  static EntitlementRequest entitlementRequest() {
    return EntitlementRequest.builder()
      .type(ENTITLE)
      .applications(List.of(APPLICATION_ID))
      .tenantId(TENANT_ID)
      .build();
  }
}
