package org.folio.entitlement.service.stage;

import static org.folio.entitlement.integration.folio.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.OKAPI_TOKEN;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.mockito.Mockito.verify;

import java.util.Map;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.service.ApplicationManagerService;
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
    var flowParameters = flowParameters(request, applicationDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, Map.of(PARAM_TENANT_NAME, TENANT_NAME));

    validator.execute(stageContext);

    verify(applicationManagerService).validate(applicationDescriptor(), OKAPI_TOKEN);
  }
}
