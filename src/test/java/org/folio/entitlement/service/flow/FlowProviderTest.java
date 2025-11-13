package org.folio.entitlement.service.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.REVOKE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.STATE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.mockito.Mockito.when;

import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.support.TestUtils;
import org.folio.flow.api.Flow;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FlowProviderTest {

  @InjectMocks private FlowProvider flowProvider;
  @Mock private EntitleFlowFactory entitleFlowFactory;
  @Mock private RevokeFlowFactory revokeFlowFactory;
  @Mock private UpgradeFlowFactory upgradeFlowFactory;
  @Mock private DesiredStateFlowFactory stateFlowFactory;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void createFlow_positive_entitleFlow() {
    var request = EntitlementRequest.builder().type(ENTITLE).build();
    var flow = Flow.builder().id("test-flow").build();
    when(entitleFlowFactory.createFlow(request)).thenReturn(flow);

    var result = flowProvider.createFlow(request);

    assertThat(result).isEqualTo(flow);
  }

  @Test
  void createFlow_positive_revokeFlow() {
    var request = EntitlementRequest.builder().type(REVOKE).build();
    var flow = Flow.builder().id("test-flow").build();
    when(revokeFlowFactory.createFlow(request)).thenReturn(flow);

    var result = flowProvider.createFlow(request);

    assertThat(result).isEqualTo(flow);
  }

  @Test
  void createFlow_positive_upgradeFlow() {
    var request = EntitlementRequest.builder().type(UPGRADE).build();
    var flow = Flow.builder().id("test-flow").build();
    when(upgradeFlowFactory.createFlow(request)).thenReturn(flow);

    var result = flowProvider.createFlow(request);

    assertThat(result).isEqualTo(flow);
  }

  @Test
  void createFlow_positive_stateFlow() {
    var request = EntitlementRequest.builder().type(STATE).build();
    var flow = Flow.builder().id("test-flow").build();
    when(stateFlowFactory.createFlow(request)).thenReturn(flow);

    var result = flowProvider.createFlow(request);

    assertThat(result).isEqualTo(flow);
  }
}
