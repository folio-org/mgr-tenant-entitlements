package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.NON_TERMINAL_STATUSES;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Map;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.support.TestUtils;
import org.folio.entitlement.support.TestValues;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class FailedApplicationFlowFinalizerTest {

  @InjectMocks private FailedApplicationFlowFinalizer failedApplicationFlowFinalizer;

  @Mock private ApplicationFlowRepository applicationFlowRepository;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    when(applicationFlowRepository.updateStatusIfCurrentIn(
      eq(APPLICATION_FLOW_ID), eq(FAILED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class))).thenReturn(1);

    var request = EntitlementRequest.builder().type(ENTITLE).build();
    var flowParameters = TestValues.flowParameters(request, TestValues.appDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, Map.of());

    failedApplicationFlowFinalizer.execute(stageContext);
  }

  @Test
  void execute_positive_applicationFlowIsAlreadyInTerminalStatus() {
    when(applicationFlowRepository.updateStatusIfCurrentIn(
      eq(APPLICATION_FLOW_ID), eq(FAILED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class))).thenReturn(0);

    var request = EntitlementRequest.builder().type(ENTITLE).build();
    var flowParameters = TestValues.flowParameters(request, TestValues.appDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, Map.of());

    failedApplicationFlowFinalizer.execute(stageContext);
  }
}
