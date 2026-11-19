package org.folio.entitlement.service.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.NON_TERMINAL_STATUSES;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_PLATFORM_NEWLY_ENTITLED;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.HashMap;
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
class PlatformEntitleApplicationFlowFinalizerTest {

  private static final String APP_ID = "platform-app-1.0.0";

  @InjectMocks private PlatformEntitleApplicationFlowFinalizer flowFinalizer;

  @Mock private FlowFinalizerStatusProvider<ApplicationStageContext> statusProvider;
  @Mock private ApplicationFlowRepository applicationFlowRepository;
  @Mock private PlatformEntitlementRepository entitlementRepository;

  @Test
  void execute_positive_newApplicationIsEntitledAndRemembered() {
    stubFinishedStatus(1);
    when(entitlementRepository.existsById("platform-app")).thenReturn(false);
    var context = stageContext(new HashMap<>());

    flowFinalizer.execute(context);

    verify(entitlementRepository).save(PlatformEntitlementEntity.of(APP_ID));
    assertThat(context.isPlatformNewlyEntitled()).isTrue();
  }

  @Test
  void execute_positive_alreadyEntitledApplicationIsNotSavedAndNotRemembered() {
    stubFinishedStatus(1);
    when(entitlementRepository.existsById("platform-app")).thenReturn(true);
    var context = stageContext(new HashMap<>());

    flowFinalizer.execute(context);

    verify(entitlementRepository, never()).save(any());
    assertThat(context.isPlatformNewlyEntitled()).isFalse();
  }

  @Test
  void execute_positive_flowAlreadyTerminal_entitlementNotSaved() {
    stubFinishedStatus(0);

    flowFinalizer.execute(stageContext(new HashMap<>()));

    verify(entitlementRepository, never()).save(any());
  }

  @Test
  void cancel_positive_newlyEntitledApplicationIsRemoved() {
    flowFinalizer.cancel(stageContext(new HashMap<>(Map.of(PARAM_PLATFORM_NEWLY_ENTITLED, true))));

    verify(entitlementRepository).deleteById("platform-app");
  }

  @Test
  void cancel_positive_entitlementOfRetriedApplicationIsKept() {
    flowFinalizer.cancel(stageContext(new HashMap<>(Map.of(PARAM_PLATFORM_NEWLY_ENTITLED, false))));

    verify(entitlementRepository, never()).deleteById(any());
  }

  private void stubFinishedStatus(int updatedRows) {
    when(statusProvider.getFinalStatus(any())).thenReturn(ExecutionStatus.FINISHED);
    when(applicationFlowRepository.updateStatusIfCurrentIn(
      eq(APPLICATION_FLOW_ID), eq(FINISHED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class)))
      .thenReturn(updatedRows);
  }

  private static ApplicationStageContext stageContext(Map<?, ?> contextData) {
    var flowParameters = Map.of(
      PARAM_APPLICATION_ID, APP_ID,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);

    return TestValues.appStageContext(FLOW_STAGE_ID, flowParameters, contextData);
  }
}
