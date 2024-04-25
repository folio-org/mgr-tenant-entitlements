package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.repository.ApplicationFlowRepository;
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
class ApplicationFlowInitializerTest {

  @InjectMocks private ApplicationFlowInitializer applicationFlowInitializer;

  @Mock private ApplicationFlowRepository applicationFlowRepository;
  @Captor private ArgumentCaptor<ApplicationFlowEntity> applicationFlowEntityCaptor;

  @AfterEach
  void tearDown() {
    verifyNoMoreInteractions(applicationFlowRepository);
  }

  @Test
  void execute_positive() {
    var entity = new ApplicationFlowEntity();
    when(applicationFlowRepository.getReferenceById(APPLICATION_FLOW_ID)).thenReturn(entity);
    when(applicationFlowRepository.save(applicationFlowEntityCaptor.capture())).thenReturn(entity);

    var flowParameters = Map.of(PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, Map.of());

    applicationFlowInitializer.execute(stageContext);

    var capturedValue = applicationFlowEntityCaptor.getValue();
    assertThat(capturedValue.getStatus()).isEqualTo(IN_PROGRESS);
  }
}
