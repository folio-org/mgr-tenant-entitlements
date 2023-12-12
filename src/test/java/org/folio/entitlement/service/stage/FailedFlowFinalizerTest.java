package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FAILED;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.folio.entitlement.domain.entity.EntitlementFlowEntity;
import org.folio.entitlement.repository.EntitlementFlowRepository;
import org.folio.flow.api.StageContext;
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
class FailedFlowFinalizerTest {

  @InjectMocks private FailedFlowFinalizer failedFlowFinalizer;

  @Mock private EntitlementFlowRepository entitlementFlowRepository;
  @Captor private ArgumentCaptor<EntitlementFlowEntity> entitlementFlowEntityCaptor;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(entitlementFlowRepository);
  }

  @Test
  void execute_positive() {
    var stageContext = StageContext.of(FLOW_STAGE_ID, emptyMap(), Map.of());

    var entity = new EntitlementFlowEntity();
    when(entitlementFlowRepository.getReferenceById(APPLICATION_FLOW_ID)).thenReturn(entity);
    when(entitlementFlowRepository.save(entitlementFlowEntityCaptor.capture())).thenReturn(entity);

    failedFlowFinalizer.execute(stageContext);

    var capturedValue = entitlementFlowEntityCaptor.getValue();
    assertThat(capturedValue.getStatus()).isEqualTo(FAILED);
  }
}
