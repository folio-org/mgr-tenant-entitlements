package org.folio.entitlement.integration.okapi.stage;

import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_TENANT_NAME;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_MODULE_DESCRIPTORS;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_NAME;
import static org.folio.entitlement.support.TestValues.entitlementEvent;
import static org.folio.entitlement.support.TestValues.okapiStageContext;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.kafka.EntitlementEventPublisher;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class OkapiModulesEventPublisherTest {

  @InjectMocks private OkapiModulesEventPublisher eventPublisher;
  @Mock private EntitlementEventPublisher entitlementEventPublisher;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    var moduleId = "mod-foo-1.0.0";
    var contextData = Map.of(PARAM_TENANT_NAME, TENANT_NAME);
    var flowParameters = Map.of(
      PARAM_REQUEST, EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build(),
      PARAM_MODULE_DESCRIPTORS, List.of(new ModuleDescriptor().id(moduleId)));
    var stageContext = okapiStageContext(FLOW_STAGE_ID, flowParameters, contextData);

    eventPublisher.execute(stageContext);

    verify(entitlementEventPublisher).publish(
      entitlementEvent(EntitlementType.ENTITLE, moduleId, TENANT_NAME, TENANT_ID));
  }
}
