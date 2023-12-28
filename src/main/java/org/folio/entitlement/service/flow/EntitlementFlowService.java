package org.folio.entitlement.service.flow;

import static java.util.Collections.emptyMap;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.reverseList;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_REQUEST;
import static org.folio.entitlement.service.flow.EntitlementFlowProviderUtils.combineStages;
import static org.folio.entitlement.utils.EntitlementServiceUtils.prepareEntitlementFlowResponse;
import static org.folio.entitlement.utils.EntitlementServiceUtils.toHashMap;

import jakarta.persistence.EntityNotFoundException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.common.domain.model.OffsetRequest;
import org.folio.common.domain.model.SearchResult;
import org.folio.entitlement.domain.dto.ApplicationFlow;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.domain.dto.EntitlementFlow;
import org.folio.entitlement.domain.dto.EntitlementStage;
import org.folio.entitlement.domain.dto.ExtendedEntitlement;
import org.folio.entitlement.domain.dto.ExtendedEntitlements;
import org.folio.entitlement.domain.entity.ApplicationDependencyEntity;
import org.folio.entitlement.domain.model.EntitlementFlowHolder;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.mapper.EntitlementFlowMapper;
import org.folio.entitlement.repository.EntitlementFlowRepository;
import org.folio.entitlement.service.ApplicationDependencyService;
import org.folio.entitlement.service.ApplicationInstallationGraph;
import org.folio.entitlement.service.ApplicationManagerService;
import org.folio.entitlement.service.EntitlementStageService;
import org.folio.flow.api.Flow;
import org.folio.flow.api.Stage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@RequiredArgsConstructor
public class EntitlementFlowService {

  private final RevokeFlowProvider revokeFlowProvider;
  private final EntitlementFlowMapper entitlementFlowMapper;
  private final EntitlementFlowProvider entitlementFlowProvider;
  private final EntitlementStageService entitlementStageService;
  private final EntitlementFlowRepository entitlementFlowRepository;
  private final ApplicationDependencyService dependencyService;
  private final ApplicationManagerService applicationManagerService;

  /**
   * Retrieves {@link EntitlementFlow} by query and pagination parameters (limit, offset).
   *
   * @param query - CQL query to search by entitlement flows
   * @param limit - a limit for the number of elements returned in the response
   * @param offset - a number of elements to skip
   * @return {@link SearchResult} object with found {@link EntitlementFlow} values
   */
  @Transactional(readOnly = true)
  public SearchResult<ApplicationFlow> find(String query, Integer limit, Integer offset) {
    var foundEntities = entitlementFlowRepository.findByCql(query, OffsetRequest.of(offset, limit));
    var mappedRecords = foundEntities.map(entitlementFlowMapper::map).getContent();
    return SearchResult.of((int) foundEntities.getTotalElements(), mappedRecords);
  }

  /**
   * Retrieves entitlement flow by its identifier.
   *
   * @param flowId - flow identifier as {@link UUID}
   * @param includeStages - defines if stages must be part of response
   * @return found {@link EntitlementFlow} object
   */
  @Transactional(readOnly = true)
  public EntitlementFlow findById(UUID flowId, boolean includeStages) {
    var foundEntities = entitlementFlowRepository.findByFlowId(flowId);
    if (foundEntities.isEmpty()) {
      throw new EntityNotFoundException("Application flows are not found for flow id: " + flowId);
    }

    var applicationFlows = mapItems(foundEntities, entitlementFlowMapper::map);
    var entitlementStages = getEntitlementStagesAsMap(flowId, includeStages);
    return prepareEntitlementFlowResponse(applicationFlows, entitlementStages);
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

    var entitlements = entitlementFlowRepository.findLastEntitlementFlows(applicationIds, tenantId);
    return mapItems(entitlements, entitlementFlowMapper::map);
  }

  /**
   * Retrieves entitled application by application names and tenant id.
   *
   * @param applicationNames - list with application names
   * @param tenantId - tenant identifier as {@link UUID}
   * @return {@link List} with entitled dependent {@link Entitlement} objects
   */
  @Transactional(readOnly = true)
  public List<ApplicationFlow> findLastFlowsByAppNames(Collection<String> applicationNames, UUID tenantId) {
    if (isEmpty(applicationNames)) {
      return Collections.emptyList();
    }

    var entitlements = entitlementFlowRepository.findLastEntitlementFlowsByAppNames(applicationNames, tenantId);
    return mapItems(entitlements, entitlementFlowMapper::map);
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

    var entitlements = entitlementFlowRepository.findLastEntitlementFlows(dependentAppIds, tenantId);

    return mapItems(entitlements, entitlementFlowMapper::map);
  }

  /**
   * Creates entitlement entities with status equal to {@link org.folio.entitlement.domain.dto.ExecutionStatus#QUEUED}.
   *
   * @param flowId - flow identifier as {@link UUID}
   * @param request - entitlement request object.
   * @return - list with created {@link ApplicationFlow} values
   */
  @Transactional
  public List<ApplicationFlow> createApplicationFlowsWithStatusQueued(UUID flowId, EntitlementRequest request) {
    var type = request.getType();
    var tenant = request.getTenantId();
    var applicationIds = request.getApplications();

    var flowEntities = mapItems(applicationIds, applicationId ->
      entitlementFlowMapper.mapWithStatusQueued(tenant, applicationId, flowId, type));
    var savedFlowEntities = entitlementFlowRepository.saveAll(flowEntities);

    return entitlementFlowMapper.map(savedFlowEntities);
  }

  /**
   * Prepares and execute entitlement flow for specified application and tenant in {@link EntitlementRequest}.
   *
   * @param flowId - flow identifier as {@link UUID}
   * @param request - entitlement request with necessary data to run entitlement flow
   * @param queuedFlows - queued application flows
   * @return entitlement flow identifier as {@link UUID}
   */
  public EntitlementFlowHolder createEntitlementFlow(UUID flowId,
    EntitlementRequest request, List<ApplicationFlow> queuedFlows) {
    if (CollectionUtils.isEmpty(queuedFlows)) {
      throw new IllegalStateException("Application flows cannot be empty");
    }

    var applicationFlowMap = toHashMap(queuedFlows, ApplicationFlow::getApplicationId, ApplicationFlow::getId);
    var lfp = new LayerFlowProvider(flowId, request, applicationFlowMap);

    var installationLayers = getAppInstallationLayers(request);
    var applicationLayerFlows = mapItems(installationLayers, lfp::prepareFlow);

    var entitlementFlow = buildEntitlementFlow(flowId, request, applicationLayerFlows);

    var entitlements = mapItems(request.getApplications(),
      applicationId -> buildEntitlement(request.getTenantId(), applicationId, applicationFlowMap.get(applicationId)));

    return EntitlementFlowHolder.of(entitlementFlow, buildEntitlements(entitlements, flowId));
  }

  /**
   * Provides an {@link org.folio.entitlement.domain.dto.ApplicationFlow} object for flow id and application id.
   *
   * @param applicationFlowId - application entitlement flow identifier as {@link UUID}
   * @param includeStages - defines if stages must be part of response
   * @return found {@link ApplicationFlow} object
   */
  @Transactional(readOnly = true)
  public ApplicationFlow findByApplicationFlowId(UUID applicationFlowId, boolean includeStages) {
    var applicationFlowEntity = entitlementFlowRepository.getReferenceById(applicationFlowId);
    var applicationFlow = entitlementFlowMapper.map(applicationFlowEntity);

    if (includeStages) {
      var entitlementStages = entitlementStageService.findEntitlementStages(applicationFlowId);
      applicationFlow.setStages(entitlementStages.getRecords());
    }

    return applicationFlow;
  }

  public void deleteApplicationFlow(UUID applicationFlowId) {
    entitlementFlowRepository.deleteById(applicationFlowId);
  }

  private Map<UUID, List<EntitlementStage>> getEntitlementStagesAsMap(UUID flowId, boolean includeStages) {
    return includeStages ? entitlementStageService.findStagesForFlow(flowId) : emptyMap();
  }

  private List<Set<String>> getAppInstallationLayers(EntitlementRequest request) {
    var descriptors = applicationManagerService.getApplicationDescriptors(request.getApplications(),
      request.getOkapiToken());

    var applicationGraph = new ApplicationInstallationGraph(descriptors);
    var result = applicationGraph.getInstallationSequence();

    return request.getType() == ENTITLE ? result : reverseList(result);
  }

  private static Flow buildEntitlementFlow(UUID flowId, EntitlementRequest request, List<? extends Stage> stages) {
    var builder = Flow.builder()
      .id(flowId)
      .executionStrategy(request.getExecutionStrategy())
      .flowParameter(PARAM_REQUEST, request);

    stages.forEach(builder::stage);

    return builder.build();
  }

  private static ExtendedEntitlement buildEntitlement(UUID tenantId, String appId, UUID flowId) {
    return new ExtendedEntitlement().tenantId(tenantId).applicationId(appId).flowId(flowId);
  }

  private static ExtendedEntitlements buildEntitlements(List<ExtendedEntitlement> entitlements, UUID flowId) {
    return new ExtendedEntitlements()
      .entitlements(entitlements)
      .totalRecords(entitlements.size())
      .flowId(flowId);
  }

  private static final class Sequence {

    private int counter;
    private final String prefix;

    private Sequence(String prefix) {
      this.prefix = prefix;
      this.counter = 0;
    }

    static Sequence withPrefix(String prefix) {
      return new Sequence(prefix);
    }

    String nextValue() {
      return prefix + counter++;
    }
  }

  private class LayerFlowProvider {

    private final EntitlementRequest request;
    private final Map<String, UUID> applicationFlowMap;
    private final Sequence seqLayerFlowId;

    LayerFlowProvider(UUID flowId, EntitlementRequest request, Map<String, UUID> applicationFlowMap) {
      this.request = request;
      this.applicationFlowMap = applicationFlowMap;
      this.seqLayerFlowId = Sequence.withPrefix(flowId + "/appi-l");
    }

    Flow prepareFlow(Set<String> layerApplicationIds) {
      var layerFlowId = seqLayerFlowId.nextValue();

      var applicationFlows = mapItems(layerApplicationIds, applicationId -> prepareApplicationFlow(
        layerFlowId + "/" + applicationFlowMap.get(applicationId), request, applicationId));

      return combineApplicationLayerFlows(layerFlowId, applicationFlows);
    }

    private Flow prepareApplicationFlow(Object applicationFlowId, EntitlementRequest request, String applicationId) {
      return request.getType() == ENTITLE
        ? entitlementFlowProvider.prepareFlow(applicationFlowId, applicationId, request.getExecutionStrategy())
        : revokeFlowProvider.prepareFlow(applicationFlowId, applicationId);
    }

    private static Flow combineApplicationLayerFlows(String flowId, List<? extends Stage> applicationFlows) {
      return Flow.builder()
        .id(flowId)
        .stage(combineStages(applicationFlows))
        .build();
    }
  }
}
