package org.folio.entitlement.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.entitlement.domain.dto.ExecutionStatus.FINISHED;
import static org.folio.entitlement.support.TestConstants.APPLICATION_FLOW_ID;
import static org.folio.entitlement.support.TestConstants.FLOW_ID;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.SearchResult;
import org.folio.entitlement.domain.dto.EntitlementStage;
import org.folio.entitlement.domain.entity.EntitlementStageEntity;
import org.folio.entitlement.domain.entity.key.EntitlementStageKey;
import org.folio.entitlement.domain.entity.type.EntityExecutionStatus;
import org.folio.entitlement.mapper.EntitlementStageMapper;
import org.folio.entitlement.repository.EntitlementStageRepository;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class EntitlementStageServiceTest {

  private static final String STAGE_NAME = "test-stage";

  @InjectMocks private EntitlementStageService entitlementStageService;
  @Mock private EntitlementStageMapper entitlementStageMapper;
  @Mock private EntitlementStageRepository entitlementStageRepository;

  @Test
  void findEntitlementStages_positive() {
    var entity = entitlementStageEntity();
    var entitlementStage = entitlementStage();

    when(entitlementStageRepository.findByApplicationFlowId(APPLICATION_FLOW_ID)).thenReturn(List.of(entity));
    when(entitlementStageMapper.map(entity)).thenReturn(entitlementStage);

    var result = entitlementStageService.findEntitlementStages(APPLICATION_FLOW_ID);

    assertThat(result).isEqualTo(SearchResult.of(List.of(entitlementStage)));
  }

  @Test
  void getEntitlementStage_positive() {
    var entity = entitlementStageEntity();
    var entitlementStage = entitlementStage();
    var entityId = EntitlementStageKey.of(APPLICATION_FLOW_ID, STAGE_NAME);

    when(entitlementStageRepository.getReferenceById(entityId)).thenReturn(entity);
    when(entitlementStageMapper.map(entity)).thenReturn(entitlementStage);

    var result = entitlementStageService.getEntitlementStage(APPLICATION_FLOW_ID, STAGE_NAME);

    assertThat(result).isEqualTo(entitlementStage);
  }

  @Test
  void findStagesForFlow_positive() {
    var entity = entitlementStageEntity();
    var entitlementStage = entitlementStage();

    when(entitlementStageRepository.findByFlowId(FLOW_ID)).thenReturn(List.of(entity));
    when(entitlementStageMapper.map(entity)).thenReturn(entitlementStage);

    var result = entitlementStageService.findStagesForFlow(FLOW_ID);

    assertThat(result).isEqualTo(Map.of(APPLICATION_FLOW_ID, List.of(entitlementStage)));
  }

  private static EntitlementStage entitlementStage() {
    return new EntitlementStage()
      .name(STAGE_NAME)
      .applicationFlowId(APPLICATION_FLOW_ID)
      .status(FINISHED);
  }

  private static EntitlementStageEntity entitlementStageEntity() {
    var entity = new EntitlementStageEntity();
    entity.setName(STAGE_NAME);
    entity.setApplicationFlowId(APPLICATION_FLOW_ID);
    entity.setStatus(EntityExecutionStatus.FINISHED);
    return entity;
  }
}
