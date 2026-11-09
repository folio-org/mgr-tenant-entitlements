package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.NON_TERMINAL_STATUSES;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Map;
import org.folio.entitlement.domain.dto.ExecutionStatus;
import org.folio.entitlement.domain.entity.PlatformEntitlementEntity;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.repository.PlatformEntitlementRepository;
import org.folio.entitlement.support.TestValues;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PlatformUpgradeApplicationFlowFinalizerTest {

  private static final String ENTITLED_ID = "platform-app-1.0.0";
  private static final String UPGRADED_ID = "platform-app-2.0.0";

  @InjectMocks private PlatformUpgradeApplicationFlowFinalizer flowFinalizer;

  @Mock private FlowFinalizerStatusProvider<ApplicationStageContext> statusProvider;
  @Mock private ApplicationFlowRepository applicationFlowRepository;
  @Mock private PlatformEntitlementRepository entitlementRepository;

  @Test
  void execute_positive_upgradedVersionReplacesEntitledVersion() {
    stubFinishedStatus(1);

    flowFinalizer.execute(stageContext(UPGRADED_ID));

    verify(entitlementRepository).save(PlatformEntitlementEntity.of(UPGRADED_ID));
  }

  @Test
  void execute_positive_flowAlreadyTerminal_entitlementNotSaved() {
    stubFinishedStatus(0);

    flowFinalizer.execute(stageContext(UPGRADED_ID));

    verify(entitlementRepository, never()).save(any());
  }

  @Test
  void cancel_positive_entitledVersionIsRestored() {
    flowFinalizer.cancel(stageContext(UPGRADED_ID));

    verify(entitlementRepository).save(PlatformEntitlementEntity.of(ENTITLED_ID));
  }

  @Test
  void cancel_positive_sameVersionRetryChangesNothing() {
    flowFinalizer.cancel(stageContext(ENTITLED_ID));

    verify(entitlementRepository, never()).save(any());
  }

  private void stubFinishedStatus(int updatedRows) {
    when(statusProvider.getFinalStatus(any())).thenReturn(ExecutionStatus.FINISHED);
    when(applicationFlowRepository.updateStatusIfCurrentIn(
      eq(APPLICATION_FLOW_ID), eq(FINISHED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class)))
      .thenReturn(updatedRows);
  }

  private static ApplicationStageContext stageContext(String applicationId) {
    var flowParameters = Map.of(
      PARAM_APPLICATION_ID, applicationId,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);

    return TestValues.appStageContext(FLOW_STAGE_ID, flowParameters, Map.of());
  }
}
