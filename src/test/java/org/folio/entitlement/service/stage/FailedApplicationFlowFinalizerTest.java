package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FAILED;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.applicationDescriptor;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.repository.ApplicationFlowRepository;
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
class FailedApplicationFlowFinalizerTest {

  @InjectMocks private FailedApplicationFlowFinalizer failedApplicationFlowFinalizer;

  @Mock private ApplicationFlowRepository applicationFlowRepository;
  @Captor private ArgumentCaptor<ApplicationFlowEntity> applicationFlowEntityCaptor;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(applicationFlowRepository);
  }

  @Test
  void execute_positive() {
    var request = EntitlementRequest.builder().type(ENTITLE).build();
    var flowParameters = TestValues.flowParameters(request, applicationDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, Map.of());

    var entity = new ApplicationFlowEntity();
    when(applicationFlowRepository.getReferenceById(APPLICATION_FLOW_ID)).thenReturn(entity);
    when(applicationFlowRepository.save(applicationFlowEntityCaptor.capture())).thenReturn(entity);

    failedApplicationFlowFinalizer.execute(stageContext);

    var capturedValue = applicationFlowEntityCaptor.getValue();
    assertThat(capturedValue.getStatus()).isEqualTo(FAILED);
  }
}
