package org.folio.entitlement.service.stage;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.CANCELLED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_STAGE_ID;
import static org.mockito.Mockito.when;

import java.util.List;
import org.folio.common.domain.model.error.Parameter;
import org.folio.entitlement.domain.entity.EntitlementStageEntity;
import org.folio.entitlement.domain.entity.key.EntitlementStageKey;
import org.folio.entitlement.integration.IntegrationException;
import org.folio.entitlement.repository.EntitlementStageRepository;
import org.folio.flow.api.StageContext;
import org.folio.test.types.UnitTest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class DatabaseLoggingStageTest {

  private static final String STAGE_NAME = "TestStage";

  @InjectMocks private TestStage testStage;
  @Mock private EntitlementStageRepository stageRepository;
  @Captor private ArgumentCaptor<EntitlementStageEntity> entitlementStageCaptor;

  @Test
  void onStart_positive() {
    when(stageRepository.save(entitlementStageCaptor.capture())).thenReturn(entitlementStageEntity());
    var stageContext = stageContext();

    testStage.onStart(stageContext);

    var capturedEntity = entitlementStageCaptor.getValue();
    assertThat(capturedEntity.getStatus()).isEqualTo(IN_PROGRESS);
    assertThat(capturedEntity.getApplicationFlowId()).isEqualTo(APPLICATION_FLOW_ID);
    assertThat(capturedEntity.getName()).isEqualTo(STAGE_NAME);
    assertThat(capturedEntity.getErrorMessage()).isNull();
  }

  @Test
  void onSuccess_positive() {
    var expectedKey = EntitlementStageKey.of(APPLICATION_FLOW_ID, STAGE_NAME);
    var entity = entitlementStageEntity();
    when(stageRepository.getReferenceById(expectedKey)).thenReturn(entity);
    when(stageRepository.save(entitlementStageCaptor.capture())).thenReturn(entity);
    var stageContext = stageContext();

    testStage.onSuccess(stageContext);

    var capturedEntity = entitlementStageCaptor.getValue();
    assertThat(capturedEntity.getStatus()).isEqualTo(FINISHED);
    assertThat(capturedEntity.getApplicationFlowId()).isEqualTo(APPLICATION_FLOW_ID);
    assertThat(capturedEntity.getName()).isEqualTo(STAGE_NAME);
    assertThat(capturedEntity.getErrorMessage()).isNull();
  }

  @Test
  void onError_positive() {
    var expectedKey = EntitlementStageKey.of(APPLICATION_FLOW_ID, STAGE_NAME);
    var entity = entitlementStageEntity();
    when(stageRepository.getReferenceById(expectedKey)).thenReturn(entity);
    when(stageRepository.save(entitlementStageCaptor.capture())).thenReturn(entity);
    var stageContext = stageContext();
    var errorMessage = "Failed to perform stage";

    testStage.onError(stageContext, new RuntimeException(errorMessage));

    var capturedEntity = entitlementStageCaptor.getValue();
    assertThat(capturedEntity.getStatus()).isEqualTo(FAILED);
    assertThat(capturedEntity.getApplicationFlowId()).isEqualTo(APPLICATION_FLOW_ID);
    assertThat(capturedEntity.getName()).isEqualTo(STAGE_NAME);
    assertThat(capturedEntity.getErrorMessage()).isEqualTo(errorMessage);
  }

  @Test
  void onError_positive_integrationException() {
    var expectedKey = EntitlementStageKey.of(APPLICATION_FLOW_ID, STAGE_NAME);
    var entity = entitlementStageEntity();
    when(stageRepository.getReferenceById(expectedKey)).thenReturn(entity);
    when(stageRepository.save(entitlementStageCaptor.capture())).thenReturn(entity);
    var stageContext = stageContext();
    var errorMessage = "Failed to perform stage";

    testStage.onError(stageContext, new IntegrationException(errorMessage, new RuntimeException("runtime error")));

    var capturedEntity = entitlementStageCaptor.getValue();
    assertThat(capturedEntity.getStatus()).isEqualTo(FAILED);
    assertThat(capturedEntity.getApplicationFlowId()).isEqualTo(APPLICATION_FLOW_ID);
    assertThat(capturedEntity.getErrorMessage()).isEqualTo("Failed to perform stage, cause: runtime error");
  }

  @Test
  void onError_positive_integrationExceptionWithParameters() {
    var expectedKey = EntitlementStageKey.of(APPLICATION_FLOW_ID, STAGE_NAME);
    var entity = entitlementStageEntity();
    when(stageRepository.getReferenceById(expectedKey)).thenReturn(entity);
    when(stageRepository.save(entitlementStageCaptor.capture())).thenReturn(entity);
    var stageContext = stageContext();
    var errorMessage = "Failed to perform stage";
    var errorParameter = new Parameter().key("routeId").value("Failed to create route");

    testStage.onError(stageContext, new IntegrationException(errorMessage, List.of(errorParameter)));

    var capturedEntity = entitlementStageCaptor.getValue();
    assertThat(capturedEntity.getStatus()).isEqualTo(FAILED);
    assertThat(capturedEntity.getApplicationFlowId()).isEqualTo(APPLICATION_FLOW_ID);
    assertThat(capturedEntity.getName()).isEqualTo(STAGE_NAME);
    assertThat(capturedEntity.getErrorMessage()).isEqualTo(
      "Failed to perform stage, parameters: [{key: routeId, value: Failed to create route}]");
  }

  @Test
  void onCancel_positive() {
    var expectedKey = EntitlementStageKey.of(APPLICATION_FLOW_ID, STAGE_NAME);
    var entity = entitlementStageEntity();
    when(stageRepository.getReferenceById(expectedKey)).thenReturn(entity);
    when(stageRepository.save(entitlementStageCaptor.capture())).thenReturn(entity);
    var stageContext = stageContext();

    testStage.onCancel(stageContext);

    var capturedEntity = entitlementStageCaptor.getValue();
    assertThat(capturedEntity.getStatus()).isEqualTo(CANCELLED);
    assertThat(capturedEntity.getApplicationFlowId()).isEqualTo(APPLICATION_FLOW_ID);
    assertThat(capturedEntity.getName()).isEqualTo(STAGE_NAME);
    assertThat(capturedEntity.getErrorMessage()).isNull();
  }

  @NotNull
  private static EntitlementStageEntity entitlementStageEntity() {
    var entity = new EntitlementStageEntity();
    entity.setApplicationFlowId(APPLICATION_FLOW_ID);
    entity.setName(STAGE_NAME);
    entity.setStatus(IN_PROGRESS);
    return entity;
  }

  @NotNull
  private static StageContext stageContext() {
    return StageContext.of(FLOW_STAGE_ID, emptyMap(), emptyMap());
  }

  private static final class TestStage extends DatabaseLoggingStage {

    @Override
    public void execute(StageContext context) {}
  }
}
