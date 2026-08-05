package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.EntitlementRequestType.UPGRADE;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.NON_TERMINAL_STATUSES;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Map;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.service.EntitlementCrudService;
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
class UpgradeApplicationFlowFinalizerTest {

  @InjectMocks private UpgradeApplicationFlowFinalizer flowFinalizer;

  @Mock private EntitlementCrudService entitlementCrudService;
  @Mock private ApplicationFlowRepository applicationFlowRepository;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    when(applicationFlowRepository.updateStatusIfCurrentIn(
      eq(APPLICATION_FLOW_ID), eq(FINISHED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class))).thenReturn(1);

    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters(), Map.of());
    flowFinalizer.execute(stageContext);

    verify(entitlementCrudService).delete(entitlement(TENANT_ID, ENTITLED_APPLICATION_ID));
    verify(entitlementCrudService).save(entitlement(TENANT_ID, APPLICATION_ID));
  }

  @Test
  void execute_positive_flowAlreadyTerminal_entitlementNotUpdated() {
    when(applicationFlowRepository.updateStatusIfCurrentIn(
      eq(APPLICATION_FLOW_ID), eq(FINISHED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class))).thenReturn(0);

    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters(), Map.of());
    flowFinalizer.execute(stageContext);

    verify(entitlementCrudService, never()).delete(any());
    verify(entitlementCrudService, never()).save(any());
  }

  private static Map<?, ?> flowParameters() {
    var entitlementRequest = EntitlementRequest.builder().type(UPGRADE).tenantId(TENANT_ID).build();
    return Map.of(
      PARAM_REQUEST, entitlementRequest,
      PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID,
      PARAM_APPLICATION_ID, APPLICATION_ID,
      PARAM_ENTITLED_APPLICATION_ID, ENTITLED_APPLICATION_ID);
  }
}
