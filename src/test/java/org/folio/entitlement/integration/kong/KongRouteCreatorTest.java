package org.folio.entitlement.integration.kong;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.folio.tools.kong.service.KongGatewayService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KongRouteCreatorTest {

  @InjectMocks private KongRouteCreator kongRouteCreator;
  @Mock private KongGatewayService kongGatewayService;

  @Test
  void execute_positive_entitleRequest() {
    var request = EntitlementRequest.builder().type(ENTITLE).build();
    var moduleDescriptors = List.of(new ModuleDescriptor());
    var applicationDescriptor = applicationDescriptor().moduleDescriptors(moduleDescriptors);
    var stageParameters = Map.of(PARAM_TENANT_NAME, TENANT_NAME, PARAM_APP_DESCRIPTOR, applicationDescriptor);
    var stageContext = StageContext.of(FLOW_STAGE_ID, Map.of(PARAM_REQUEST, request), stageParameters);

    kongRouteCreator.execute(stageContext);

    verify(kongGatewayService).addRoutes(TENANT_NAME, moduleDescriptors);
  }
}
