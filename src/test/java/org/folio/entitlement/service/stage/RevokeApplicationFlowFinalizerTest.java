package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.REVOKE;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FINISHED;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.support.TestUtils;
import org.folio.entitlement.support.TestValues;
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
class RevokeApplicationFlowFinalizerTest {

  @InjectMocks private RevokeApplicationFlowFinalizer revokeApplicationFlowFinalizer;

  @Mock private EntitlementCrudService entitlementCrudService;
  @Mock private ApplicationFlowRepository applicationFlowRepository;
  @Captor private ArgumentCaptor<ApplicationFlowEntity> applicationFlowEntityCaptor;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    var entitlementRequest = EntitlementRequest.builder().type(REVOKE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(entitlementRequest, TestValues.appDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    var entity = new ApplicationFlowEntity();
    when(applicationFlowRepository.getReferenceById(APPLICATION_FLOW_ID)).thenReturn(entity);
    when(applicationFlowRepository.save(applicationFlowEntityCaptor.capture())).thenReturn(entity);

    revokeApplicationFlowFinalizer.execute(stageContext);

    var capturedValue = applicationFlowEntityCaptor.getValue();
    assertThat(capturedValue.getStatus()).isEqualTo(FINISHED);
    verify(entitlementCrudService).delete(entitlement(TENANT_ID, APPLICATION_ID));
  }
}
