package org.folio.entitlement.integration.kong;

import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KongRouteCleanerTest {

  @InjectMocks private KongRouteCleaner kongRouteCleaner;
  @Mock private KongGatewayService kongGatewayService;

  @Test
  void execute_positive_purgeTrue() {
    var request = EntitlementRequest.builder().type(REVOKE).purge(true).build();
    var applicationDescriptor = applicationDescriptor();
    var stageParameters = Map.of(PARAM_APP_DESCRIPTOR, applicationDescriptor, PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = StageContext.of(FLOW_STAGE_ID, Map.of(PARAM_REQUEST, request), stageParameters);

    kongRouteCleaner.execute(stageContext);

    verify(kongGatewayService).removeRoutes(TENANT_NAME, applicationDescriptor);
  }

  @Test
  void execute_positive_purgeFalse() {
    var request = EntitlementRequest.builder().type(REVOKE).purge(false).build();
    var applicationDescriptor = applicationDescriptor();
    var stageParameters = Map.of(PARAM_APP_DESCRIPTOR, applicationDescriptor, PARAM_TENANT_NAME, TENANT_NAME);
    var stageContext = StageContext.of(FLOW_STAGE_ID, Map.of(PARAM_REQUEST, request), stageParameters);

    kongRouteCleaner.execute(stageContext);

    verify(kongGatewayService, never()).removeRoutes(TENANT_NAME, applicationDescriptor);
  }
}
