package org.folio.entitlement.service;

import static java.util.stream.Collectors.groupingBy;
import static org.folio.common.utils.CollectionUtils.mapItems;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.SearchResult;
import org.folio.entitlement.domain.dto.EntitlementStage;
import org.folio.entitlement.domain.entity.key.EntitlementStageKey;
import org.folio.entitlement.mapper.EntitlementStageMapper;
import org.folio.entitlement.repository.EntitlementStageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EntitlementStageService {

  private final EntitlementStageMapper entitlementStageMapper;
  private final EntitlementStageRepository entitlementStageRepository;

  /**
   * Provides a list of entitlement stages for flow id and application id.
   *
   * @param applicationFlowId - application flow identifier as {@link UUID}
   * @return {@link SearchResult} with {@link EntitlementStage} values
   */
  @Transactional(readOnly = true)
  public SearchResult<EntitlementStage> findEntitlementStages(UUID applicationFlowId) {
    var foundEntities = entitlementStageRepository.findByApplicationFlowId(applicationFlowId);
    return SearchResult.of(foundEntities.size(), mapItems(foundEntities, entitlementStageMapper::map));
  }

  /**
   * Provides entitlement stage result for flow id, application id and stage name.
   *
   * @param applicationFlowId - application flow identifier as {@link UUID}
   * @param stageName - stage name as {@link String}
   * @return {@link EntitlementStage} result object
   */
  @Transactional(readOnly = true)
  public EntitlementStage getEntitlementStage(UUID applicationFlowId, String stageName) {
    var entity = entitlementStageRepository.getReferenceById(EntitlementStageKey.of(applicationFlowId, stageName));
    return entitlementStageMapper.map(entity);
  }

  /**
   * Founds entitlement stages by flow id and returns them as map grouped by application flow id.
   *
   * @param flowId - entitlement flow identifier as {@link UUID}
   * @return {@link Map} with entitlement stages, where key is the application entitlement flow id
   */
  @Transactional(readOnly = true)
  public Map<UUID, List<EntitlementStage>> findStagesForFlow(UUID flowId) {
    var entitlementStageEntities = entitlementStageRepository.findByFlowId(flowId);
    var values = mapItems(entitlementStageEntities, entitlementStageMapper::map);
    return values.stream().collect(groupingBy(EntitlementStage::getApplicationFlowId));
  }
}
