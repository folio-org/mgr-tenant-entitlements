package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_APP_ID;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.folio.entitlement.domain.entity.EntitlementFlowEntity;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.repository.EntitlementFlowRepository;
import org.folio.entitlement.service.EntitlementCrudService;
import org.folio.entitlement.support.TestUtils;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class RevokeFlowFinalizerTest {

  @InjectMocks private RevokeFlowFinalizer revokeFlowFinalizer;

  @Mock private EntitlementCrudService entitlementCrudService;
  @Mock private EntitlementFlowRepository entitlementFlowRepository;
  @Captor private ArgumentCaptor<EntitlementFlowEntity> entitlementFlowEntityCaptor;

  @BeforeEach
  void setUp() {
    revokeFlowFinalizer.setEntitlementFlowRepository(entitlementFlowRepository);
  }

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_revoke_positive() {
    var entitlementRequest = EntitlementRequest.builder().type(REVOKE).tenantId(TENANT_ID).build();
    var flowParameters = Map.of(PARAM_REQUEST, entitlementRequest, PARAM_APP_ID, APPLICATION_ID);
    var stageContext = StageContext.of(FLOW_STAGE_ID, flowParameters, Map.of());

    var entity = new EntitlementFlowEntity();
    when(entitlementFlowRepository.getReferenceById(APPLICATION_FLOW_ID)).thenReturn(entity);
    when(entitlementFlowRepository.save(entitlementFlowEntityCaptor.capture())).thenReturn(entity);

    revokeFlowFinalizer.execute(stageContext);

    var capturedValue = entitlementFlowEntityCaptor.getValue();
    assertThat(capturedValue.getStatus()).isEqualTo(FINISHED);
    verify(entitlementCrudService).delete(entitlement(TENANT_ID, APPLICATION_ID));
  }
}
