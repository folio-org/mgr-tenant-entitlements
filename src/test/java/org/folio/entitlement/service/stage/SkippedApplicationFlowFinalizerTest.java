package org.folio.entitlement.service.stage;

import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.QUEUED;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.domain.model.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.APPLICATION_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.folio.entitlement.support.TestConstants.TENANT_ID;
import static org.folio.entitlement.support.TestValues.appStageContext;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.folio.entitlement.domain.entity.ApplicationFlowEntity;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.repository.ApplicationFlowRepository;
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
class SkippedApplicationFlowFinalizerTest {

  @InjectMocks SkippedApplicationFlowFinalizer flowFinalizer;
  @Mock private ApplicationFlowRepository applicationFlowRepository;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void execute_positive() {
    var entity = applicationFlowEntity(QUEUED);
    when(applicationFlowRepository.getReferenceById(APPLICATION_FLOW_ID)).thenReturn(entity);

    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters(), Map.of());
    flowFinalizer.execute(stageContext);

    verify(applicationFlowRepository).delete(entity);
  }

  @Test
  void execute_positive_statusIsNotFinished() {
    var entity = applicationFlowEntity(IN_PROGRESS);
    when(applicationFlowRepository.getReferenceById(APPLICATION_FLOW_ID)).thenReturn(entity);

    var stageContext = appStageContext(FLOW_STAGE_ID, flowParameters(), Map.of());
    flowFinalizer.execute(stageContext);

    verify(applicationFlowRepository, never()).delete(entity);
  }

  private static ApplicationFlowEntity applicationFlowEntity(EntityExecutionStatus status) {
    var entity = new ApplicationFlowEntity();
    entity.setId(APPLICATION_FLOW_ID);
    entity.setApplicationId(APPLICATION_ID);
    entity.setTenantId(TENANT_ID);
    entity.setStatus(status);
    return entity;
  }

  private static Map<?, ?> flowParameters() {
    var entitlementRequest = EntitlementRequest.builder().type(ENTITLE).tenantId(TENANT_ID).build();
    return Map.of(PARAM_REQUEST, entitlementRequest, PARAM_APPLICATION_FLOW_ID, APPLICATION_FLOW_ID);
  }
}
