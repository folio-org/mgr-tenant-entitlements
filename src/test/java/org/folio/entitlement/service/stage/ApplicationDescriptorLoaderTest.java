package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class ApplicationDescriptorLoaderTest {

  @InjectMocks private ApplicationDescriptorLoader applicationDescriptorLoader;
  @Mock private ApplicationManagerService applicationManagerService;

  @Test
  void execute_positive_entitleRequest() {
    var stageContext = stageContext(ENTITLE);
    var applicationDescriptor = applicationDescriptor();

    when(applicationManagerService.getApplicationDescriptor(APPLICATION_ID, OKAPI_TOKEN))
      .thenReturn(applicationDescriptor);

    applicationDescriptorLoader.execute(stageContext);

    assertThat(stageContext.<ApplicationDescriptor>get(PARAM_APP_DESCRIPTOR)).isEqualTo(applicationDescriptor);
  }

  @Test
  void execute_positive_revokeRequest() {
    var stageContext = stageContext(REVOKE);
    var applicationDescriptor = applicationDescriptor();

    when(applicationManagerService.getApplicationDescriptor(APPLICATION_ID, OKAPI_TOKEN))
      .thenReturn(applicationDescriptor);

    applicationDescriptorLoader.execute(stageContext);

    assertThat(stageContext.<ApplicationDescriptor>get(PARAM_APP_DESCRIPTOR)).isEqualTo(applicationDescriptor);
  }

  private StageContext stageContext(EntitlementType type) {
    var request = EntitlementRequest.builder().tenantId(TENANT_ID).type(type).okapiToken(OKAPI_TOKEN).build();
    var flowParameters = Map.of(PARAM_APP_ID, APPLICATION_ID, PARAM_REQUEST, request);
    return StageContext.of(FLOW_STAGE_ID, flowParameters, emptyMap());
  }
}
