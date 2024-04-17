package org.folio.entitlement.service;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.SearchResult;
import org.folio.entitlement.domain.dto.FlowStage;
import org.folio.entitlement.domain.entity.key.FlowStageKey;
import org.folio.entitlement.mapper.FlowStageMapper;
import org.folio.entitlement.repository.FlowStageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FlowStageService {

  private final FlowStageMapper flowStageMapper;
  private final FlowStageRepository flowStageRepository;

  /**
   * Provides a list of entitlement stages for flow id and application id.
   *
   * @param flowId - application flow identifier as {@link UUID}
   * @return {@link SearchResult} with {@link FlowStage} values
   */
  @Transactional(readOnly = true)
  public SearchResult<FlowStage> findByFlowId(UUID flowId) {
    var foundEntities = flowStageRepository.findByFlowId(flowId);
    return SearchResult.of(foundEntities.size(), mapItems(foundEntities, flowStageMapper::map));
  }

  /**
   * Provides a list of entitlement stages for flow id and application id.
   *
   * @param applicationFlowIds - application flow identifier as {@link UUID}
   * @return {@link SearchResult} with {@link FlowStage} values
   */
  @Transactional(readOnly = true)
  public Map<UUID, List<FlowStage>> findByFlowIds(List<UUID> applicationFlowIds) {
    if (isEmpty(applicationFlowIds)) {
      return emptyMap();
    }

    var foundEntities = flowStageRepository.findByFlowIds(applicationFlowIds);
    return foundEntities.stream()
      .map(flowStageMapper::map)
      .collect(groupingBy(FlowStage::getFlowId));
  }

  /**
   * Provides entitlement stage result for flow id, application id and stage name.
   *
   * @param applicationFlowId - application flow identifier as {@link UUID}
   * @param stageName - stage name as {@link String}
   * @return {@link FlowStage} result object
   */
  @Transactional(readOnly = true)
  public FlowStage getEntitlementStage(UUID applicationFlowId, String stageName) {
    var entity = flowStageRepository.getReferenceById(FlowStageKey.of(applicationFlowId, stageName));
    return flowStageMapper.map(entity);
  }

  /**
   * Founds entitlement stages by flow id and returns them as map grouped by application flow id.
   *
   * @param flowId - entitlement flow identifier as {@link UUID}
   * @return {@link Map} with entitlement stages, where key is the application entitlement flow id
   */
  @Transactional(readOnly = true)
  public Map<UUID, List<FlowStage>> findStagesForFlow(UUID flowId) {
    var entitlementStageEntities = flowStageRepository.findByFlowId(flowId);
    var values = mapItems(entitlementStageEntities, flowStageMapper::map);
    return values.stream().collect(groupingBy(FlowStage::getFlowId));
  }

  /**
   * Returns last failed stage by the flow identifier.
   *
   * <p>Last failed stage is usually is the cause of the issue in flow</p>
   *
   * @param flowId - flow identifier as {@link UUID} object.
   * @return last failed stage by the flow identifier.
   */
  public List<FlowStage> findFailedStages(UUID flowId) {
    return mapItems(flowStageRepository.findLastFailedStage(flowId), flowStageMapper::map);
  }
}
