package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.groupingBy;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.folio.common.utils.CollectionUtils.mapItems;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.common.domain.model.SearchResult;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.entity.ApplicationDependencyEntity;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.mapper.ApplicationFlowMapper;
import org.folio.entitlement.repository.ApplicationFlowRepository;
import org.folio.entitlement.service.ApplicationDependencyService;
import org.folio.entitlement.service.FlowStageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class ApplicationFlowService {

  private final ApplicationFlowMapper applicationFlowMapper;
  private final FlowStageService flowStageService;
  private final ApplicationFlowRepository applicationFlowRepository;
  private final ApplicationDependencyService dependencyService;

  /**
   * Provides an {@link ApplicationFlow} object for flow id and application id.
   *
   * @param applicationFlowId - application entitlement flow identifier as {@link UUID}
   * @param includeStages - defines if stages must be part of response
   * @return found {@link ApplicationFlow} object
   */
  @Transactional(readOnly = true)
  public ApplicationFlow getById(UUID applicationFlowId, boolean includeStages) {
    var applicationFlowEntity = applicationFlowRepository.getReferenceById(applicationFlowId);
    var applicationFlow = applicationFlowMapper.map(applicationFlowEntity);

    if (includeStages) {
      var entitlementStages = flowStageService.findByFlowId(applicationFlowId);
      applicationFlow.setStages(entitlementStages.getRecords());
    }

    return applicationFlow;
  }

  /**
   * Retrieves {@link ApplicationFlow} by query and pagination parameters (limit, offset).
   *
   * @param query - CQL query to search by entitlement flows
   * @param limit - a limit for the number of elements returned in the response
   * @param offset - a number of elements to skip
   * @return {@link SearchResult} object with found {@link ApplicationFlow} values
   */
  @Transactional(readOnly = true)
  public SearchResult<ApplicationFlow> find(String query, Integer limit, Integer offset) {
    var pageable = OffsetRequest.of(offset, limit);
    var foundEntities = isNotBlank(query)
      ? applicationFlowRepository.findByCql(query, pageable)
      : applicationFlowRepository.findAll(pageable);

    var mappedRecords = foundEntities.map(applicationFlowMapper::map).getContent();
    return SearchResult.of((int) foundEntities.getTotalElements(), mappedRecords);
  }

  /**
   * Retrieves entitled application by application ids and tenant id.
   *
   * @param applicationIds - list with application identifiers
   * @param tenantId - tenant identifier as {@link UUID}
   * @return {@link List} with entitled dependent {@link Entitlement} objects
   */
  @Transactional(readOnly = true)
  public List<ApplicationFlow> findLastFlows(List<String> applicationIds, UUID tenantId) {
    if (isEmpty(applicationIds)) {
      return Collections.emptyList();
    }

    var entitlements = applicationFlowRepository.findLastFlows(applicationIds, tenantId);
    return mapItems(entitlements, applicationFlowMapper::map);
  }

  /**
   * Retrieves entitled application by application names and tenant id.
   *
   * @param applicationNames - list with application names
   * @param tenantId - tenant identifier as {@link UUID}
   * @return {@link List} with entitled dependent {@link Entitlement} objects
   */
  @Transactional(readOnly = true)
  public List<ApplicationFlow> findLastFlowsByNames(Collection<String> applicationNames, UUID tenantId) {
    if (isEmpty(applicationNames)) {
      return Collections.emptyList();
    }

    var entitlements = applicationFlowRepository.findLastFlowsByApplicationNames(applicationNames, tenantId);
    return mapItems(entitlements, applicationFlowMapper::map);
  }

  /**
   * Retrieves dependent entitled applications by application id and tenant id.
   *
   * @param parentApplicationId - application identifier as {@link String}
   * @param tenantId - tenant identifier as {@link UUID}
   * @return {@link List} with entitled dependent {@link Entitlement} objects
   */
  @Transactional(readOnly = true)
  public List<ApplicationFlow> findLastDependentFlows(String parentApplicationId, UUID tenantId) {
    var dependencies = dependencyService.findAllByParentApplicationName(tenantId, parentApplicationId);
    var dependentAppIds = mapItems(dependencies, ApplicationDependencyEntity::getApplicationId);

    var entitlements = applicationFlowRepository.findLastFlows(dependentAppIds, tenantId);

    return mapItems(entitlements, applicationFlowMapper::map);
  }

  /**
   * Retrieves {@link ApplicationFlow} objects by root flow id.
   *
   * @param flowId - root flow identifier as {@link UUID}
   * @param includeStages - defines if stages must be part of response
   * @return {@link List} with {@link ApplicationFlow} entities
   */
  public List<ApplicationFlow> findByFlowId(UUID flowId, boolean includeStages) {
    var foundEntities = applicationFlowRepository.findByFlowId(flowId);
    var applicationFlows = mapItems(foundEntities, applicationFlowMapper::map);

    if (includeStages) {
      var applicationFlowIds = mapItems(applicationFlows, ApplicationFlow::getId);
      var entitlementStages = flowStageService.findByFlowIds(applicationFlowIds);
      for (var applicationFlow : applicationFlows) {
        var applicationId = applicationFlow.getId();
        applicationFlow.stages(entitlementStages.getOrDefault(applicationId, emptyList()));
      }
    }

    return applicationFlows;
  }

  /**
   * Retrieves {@link ApplicationFlow} objects by root flow ids.
   *
   * @param flowIds - root flow identifiers as list with {@link UUID}
   * @return {@link List} with {@link ApplicationFlow} entities
   */
  public Map<UUID, List<ApplicationFlow>> findByFlowIds(List<UUID> flowIds) {
    if (CollectionUtils.isEmpty(flowIds)) {
      return emptyMap();
    }

    return applicationFlowRepository.findByFlowIds(flowIds).stream()
      .map(applicationFlowMapper::map)
      .collect(groupingBy(ApplicationFlow::getFlowId));
  }

  /**
   * Creates {@link ApplicationFlow} entities with status
   * {@link org.folio.entitlement.domain.dto.ExecutionStatus#QUEUED} for {@link EntitlementRequest} object.
   *
   * @param flowId - control flow identifier as {@link UUID} object
   * @param request - entitlement request
   * @return {@link List} with created {@link ApplicationFlow} entities
   */
  @Transactional
  public List<ApplicationFlow> createQueuedApplicationFlow(UUID flowId, EntitlementRequest request) {
    var type = request.getType();
    var tenantId = request.getTenantId();
    var applicationIds = request.getApplications();

    var flowEntities = mapItems(applicationIds, appId ->
      applicationFlowMapper.mapWithStatusQueued(tenantId, appId, flowId, type));
    var savedFlowEntities = applicationFlowRepository.saveAll(flowEntities);

    return mapItems(savedFlowEntities, applicationFlowMapper::map);
  }

  @Transactional
  public void removeAllQueuedFlows(UUID flowId) {
    applicationFlowRepository.removeQueuedFlows(flowId);
  }
}
