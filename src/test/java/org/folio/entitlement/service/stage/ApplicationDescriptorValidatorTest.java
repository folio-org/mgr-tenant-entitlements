package org.folio.entitlement.service.stage;

import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_DESCRIPTOR;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
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
class ApplicationDescriptorValidatorTest {

  @InjectMocks private ApplicationDescriptorValidator validator;
  @Mock private ApplicationManagerService applicationManagerService;

  @Test
  void execute_entitleRequest() {
    var request = EntitlementRequest.builder().type(EntitlementType.ENTITLE).okapiToken(OKAPI_TOKEN).build();
    var contextParams = Map.of(PARAM_APP_DESCRIPTOR, applicationDescriptor());
    var stageContext = StageContext.of(FLOW_STAGE_ID, Map.of(PARAM_REQUEST, request), contextParams);

    validator.execute(stageContext);

    verify(applicationManagerService).validate(applicationDescriptor(), OKAPI_TOKEN);
  }
}
