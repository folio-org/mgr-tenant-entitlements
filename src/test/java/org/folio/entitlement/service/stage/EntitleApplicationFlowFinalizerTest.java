package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyMap;
import static org.folio.entitlement.domain.dto.EntitlementRequestType.ENTITLE;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.NON_TERMINAL_STATUSES;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.folio.entitlement.support.TestValues.entitlement;
import static org.folio.entitlement.support.TestValues.flowParameters;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.service.EntitlementCrudService;
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
class EntitleApplicationFlowFinalizerTest {

  @InjectMocks private EntitleApplicationFlowFinalizer flowFinalizer;

  @Mock private EntitlementCrudService entitlementCrudService;
  @Mock private ApplicationFlowRepository applicationFlowRepository;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_entitle_positive() {
    when(applicationFlowRepository.updateStatusIfCurrentIn(
      eq(APPLICATION_FLOW_ID), eq(FINISHED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class))).thenReturn(1);

    var entitlementRequest = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(entitlementRequest, TestValues.appDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    flowFinalizer.execute(stageContext);

    verify(entitlementCrudService).save(entitlement(TENANT_ID, APPLICATION_ID));
  }

  @Test
  void execute_positive_flowAlreadyTerminal_entitlementNotSaved() {
    when(applicationFlowRepository.updateStatusIfCurrentIn(
      eq(APPLICATION_FLOW_ID), eq(FINISHED), eq(NON_TERMINAL_STATUSES), any(ZonedDateTime.class))).thenReturn(0);

    var entitlementRequest = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(entitlementRequest, TestValues.appDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());

    flowFinalizer.execute(stageContext);

    verify(entitlementCrudService, never()).save(any());
  }

  @Test
  void execute_cancel_positive() {
    var entitlementRequest = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    var flowParameters = flowParameters(entitlementRequest, TestValues.appDescriptor());
    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters, emptyMap());
    flowFinalizer.cancel(stageContext);
    verify(entitlementCrudService).delete(entitlement(TENANT_ID, APPLICATION_ID));
  }
}
