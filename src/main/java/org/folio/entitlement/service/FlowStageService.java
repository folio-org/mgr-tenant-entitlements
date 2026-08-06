package org.folio.entitlement.service;

import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FAILED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.FINISHED;
import static org.folio.entitlement.domain.entity.type.EntityExecutionStatus.IN_PROGRESS;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

  private final FlowStageMapper mapper;
  private final FlowStageRepository repository;

  @Transactional(readOnly = true)
  public Optional<FlowStage> findById(UUID id) {
    return repository.findByStageId(id).map(mapper::map);
  }

  /**
   * Provides a list of entitlement stages for flow id and application id.
   *
   * @param flowId - application flow identifier as {@link UUID}
   * @return {@link SearchResult} with {@link FlowStage} values
   */
  @Transactional(readOnly = true)
  public SearchResult<FlowStage> findByFlowId(UUID flowId) {
    var foundEntities = repository.findByFlowId(flowId);
    return SearchResult.of(foundEntities.size(), mapItems(foundEntities, mapper::map));
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

    var foundEntities = repository.findByFlowIds(applicationFlowIds);
    return foundEntities.stream()
      .map(mapper::map)
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
    var entity = repository.getReferenceById(FlowStageKey.of(applicationFlowId, stageName));
    return mapper.map(entity);
  }

  /**
   * Founds entitlement stages by flow id and returns them as map grouped by application flow id.
   *
   * @param flowId - entitlement flow identifier as {@link UUID}
   * @return {@link Map} with entitlement stages, where key is the application entitlement flow id
   */
  @Transactional(readOnly = true)
  public Map<UUID, List<FlowStage>> findStagesForFlow(UUID flowId) {
    var entitlementStageEntities = repository.findByFlowId(flowId);
    var values = mapItems(entitlementStageEntities, mapper::map);
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
  @Transactional(readOnly = true)
  public List<FlowStage> findFailedStages(UUID flowId) {
    return mapItems(repository.findLastFailedStage(flowId), mapper::map);
  }

  /**
   * Marks all in-progress stages of the flow and of its application flows as failed.
   *
   * @param flowId - flow identifier as {@link UUID} object
   * @param finishedAt - timestamp to set as the stage finish time
   * @return number of updated stage rows
   */
  @Transactional
  public int failNonTerminalStages(UUID flowId, ZonedDateTime finishedAt) {
    // not NON_TERMINAL_STATUSES: the entitlement_stage_status_type enum in the database has no QUEUED value,
    // stage rows are created with IN_PROGRESS
    return repository.updateStatusByFlowIdIfCurrentIn(flowId, FAILED, Set.of(IN_PROGRESS), finishedAt);
  }

  @Transactional
  public int finishActiveStage(UUID id, ZonedDateTime finishedAt) {
    return repository.updateStatusByStageIdIfCurrentIn(id, FINISHED, Set.of(IN_PROGRESS), finishedAt);
  }

  @Transactional
  public int failActiveStage(UUID id, String errDetails, ZonedDateTime finishedAt) {
    return repository.markFailedByStageIdIfCurrentIn(id, errDetails, Set.of(IN_PROGRESS), finishedAt);
  }
}
