package org.folio.entitlement.service.flow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.folio.entitlement.service.flow.PlatformTestValues.platformRequest;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.folio.flow.api.Flow;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;

@UnitTest
class PlatformFlowProviderTest {

  private static final String APP_ID = "app-platform-1.0.0";

  private final PlatformEntitleFlowFactory entitleFactory = mock(PlatformEntitleFlowFactory.class);
  private final PlatformUpgradeFlowFactory upgradeFactory = mock(PlatformUpgradeFlowFactory.class);
  private final PlatformFlowProvider provider = new PlatformFlowProvider(entitleFactory, upgradeFactory);

  @Test
  void createFlow_positive_entitleRequestUsesEntitleFactory() {
    var request = platformRequest(APP_ID, ENTITLE, OKAPI_TOKEN);
    var flow = mock(Flow.class);
    when(entitleFactory.createFlow(FLOW_ID, request)).thenReturn(flow);

    assertThat(provider.createFlow(FLOW_ID, request)).isSameAs(flow);
    verifyNoInteractions(upgradeFactory);
  }

  @Test
  void createFlow_positive_upgradeRequestUsesUpgradeFactory() {
    var request = platformRequest(APP_ID, UPGRADE, OKAPI_TOKEN);
    var flow = mock(Flow.class);
    when(upgradeFactory.createFlow(FLOW_ID, request)).thenReturn(flow);

    assertThat(provider.createFlow(FLOW_ID, request)).isSameAs(flow);
    verifyNoInteractions(entitleFactory);
  }
}
