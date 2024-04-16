package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLATION_FAILED;
import static org.folio.entitlement.integration.folio.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.integration.folio.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class CancellationFailedApplicationFlowFinalizerTest {

  @InjectMocks private CancellationFailedApplicationFlowFinalizer entitlementFlowFinalizer;

  @Mock private ApplicationFlowRepository applicationFlowRepository;
  @Captor private ArgumentCaptor<ApplicationFlowEntity> applicationFlowEntityCaptor;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_entitle_positive() {
    var entity = new ApplicationFlowEntity();
    entity.setApplicationId(APPLICATION_ID);
    entity.setTenantId(TENANT_ID);

    when(applicationFlowRepository.getReferenceById(APPLICATION_FLOW_ID)).thenReturn(entity);
    when(applicationFlowRepository.save(applicationFlowEntityCaptor.capture())).thenReturn(entity);

    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters(), Map.of());
    entitlementFlowFinalizer.execute(stageContext);

    var capturedValue = applicationFlowEntityCaptor.getValue();
    assertThat(capturedValue.getStatus()).isEqualTo(CANCELLATION_FAILED);
  }

  private static Map<?, ?> flowParameters() {
    var entitlementRequest = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    return Map.of(PARAM_REQUEST, entitlementRequest, PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
  }
}
