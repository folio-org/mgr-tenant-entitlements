package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyList;
import static org.folio.common.utils.CollectionUtils.mapItems;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.common.domain.model.SearchResult;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.Flow;
import org.folio.entitlement.domain.entity.FlowEntity;
import org.folio.entitlement.mapper.FlowMapper;
import org.folio.entitlement.repository.FlowRepository;
import org.folio.entitlement.service.FlowStageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class FlowService {

  private final FlowMapper flowMapper;
  private final FlowRepository flowRepository;
  private final FlowStageService flowStageService;
  private final ApplicationFlowService applicationFlowService;

  /**
   * Retrieves {@link ApplicationFlow} by query and pagination parameters (limit, offset).
   *
   * @param query - CQL query to search by entitlement flows
   * @param limit - a limit for the number of elements returned in the response
   * @param offset - a number of elements to skip
   * @return {@link SearchResult} object with found {@link ApplicationFlow} values
   */
  @Transactional(readOnly = true)
  public SearchResult<Flow> find(String query, Integer limit, Integer offset) {
    var foundEntities = flowRepository.findByCql(query, OffsetRequest.of(offset, limit));
    var flowIds = mapItems(foundEntities.getContent(), FlowEntity::getId);
    var applicationFlowIdsMap = applicationFlowService.findByFlowIds(flowIds);

    var flows = foundEntities.stream()
      .map(flowMapper::map)
      .map(e -> e.applicationFlows(applicationFlowIdsMap.getOrDefault(e.getId(), emptyList())))
      .toList();

    return SearchResult.of((int) foundEntities.getTotalElements(), flows);
  }

  /**
   * Retrieves entitlement flow by its identifier.
   *
   * @param flowId - flow identifier as {@link UUID}
   * @param includeStages - defines if stages must be part of response
   * @return found {@link org.folio.entitlement.domain.dto.Flow} object
   * @throws jakarta.persistence.EntityNotFoundException if flow is not found by id
   */
  @Transactional(readOnly = true)
  public Flow getById(UUID flowId, boolean includeStages) {
    var flowEntity = flowRepository.getReferenceById(flowId);
    var flow = flowMapper.map(flowEntity);
    var applicationFlows = applicationFlowService.findByFlowId(flowId, includeStages);

    if (includeStages) {
      flow.stages(flowStageService.findByFlowId(flowId).getRecords());
    }

    return flow.applicationFlows(applicationFlows);
  }

  /**
   * Creates flow entity in database.
   *
   * @param flow - flow representation
   * @return created {@link Flow} entity
   */
  @Transactional
  public Flow create(Flow flow) {
    var flowEntity = flowMapper.map(flow);
    var savedEntity = flowRepository.save(flowEntity);
    return flowMapper.map(savedEntity);
  }
}
