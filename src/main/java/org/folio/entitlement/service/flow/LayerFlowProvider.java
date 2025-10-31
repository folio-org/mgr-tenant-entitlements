package org.folio.entitlement.service.flow;

import static java.util.Objects.requireNonNull;
import static java.util.function.Function.identity;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.reverseList;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ENTITLEMENT_TYPE;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.utils.EntitlementServiceUtils.toHashMap;
import static org.folio.entitlement.utils.EntitlementServiceUtils.toUnmodifiableMap;
import static org.folio.entitlement.utils.FlowUtils.combineStages;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.collections4.MapUtils;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.model.ApplicationEntitlement;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.CommonStageContext;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.Sequence;
import org.folio.entitlement.service.ApplicationInstallationGraph;
import org.folio.flow.api.Flow;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;
import org.springframework.stereotype.Component;

@Component
public class LayerFlowProvider {

  private final Map<EntitlementType, ApplicationFlowFactory> applicationFlowFactories;

  public LayerFlowProvider(List<ApplicationFlowFactory> flowFactories) {
    this.applicationFlowFactories = toUnmodifiableMap(flowFactories, ApplicationFlowFactory::getEntitlementType);
  }

  public List<Stage<? extends StageContext>> prepareLayeredFlowsReversed(CommonStageContext stageContext,
    List<ApplicationEntitlement> appEntitlements) {
    return prepareLayeredFlows(stageContext, appEntitlements, true);
  }

  public List<Stage<? extends StageContext>> prepareLayeredFlows(CommonStageContext stageContext,
    List<ApplicationEntitlement> appEntitlements) {
    return prepareLayeredFlows(stageContext, appEntitlements, false);
  }

  /**
   * Prepares layered flows for application entitlements by creating application flows from each layer
   * and combining them into a single flow per layer.
   *
   * @param stageContext       - stage context to take data from
   * @param appEntitlements    - list of application entitlements
   * @param reversedLayerOrder - flag to indicate if the layer order should be reversed
   * @return list of prepared stages for each layer
   */
  private List<Stage<? extends StageContext>> prepareLayeredFlows(CommonStageContext stageContext,
    List<ApplicationEntitlement> appEntitlements, boolean reversedLayerOrder) {
    var descriptors = mapItems(appEntitlements, ApplicationEntitlement::descriptor);

    var layers = getApplicationDescriptorsLayers(descriptors, reversedLayerOrder);

    var lfb = new LayeredFlowsBuilder(stageContext, appEntitlements, applicationFlowFactories);
    return lfb.build(layers);
  }

  /**
   * Organizes application descriptors into layers based on their dependencies.
   *
   * @param list     - list of application descriptors
   * @param reversed - flag to indicate if the layer order should be reversed
   * @return list of sets, where each set contains application ids in a layer
   */
  private List<Set<String>> getApplicationDescriptorsLayers(List<ApplicationDescriptor> list, boolean reversed) {
    var applicationGraph = new ApplicationInstallationGraph(list);

    var result = applicationGraph.getInstallationSequence();

    return !reversed ? result : reverseList(result);
  }

  private static final class LayeredFlowsBuilder {

    private final Map<EntitlementType, ApplicationFlowFactory> applicationFlowFactories;

    private final EntitlementRequest request;
    private final Map<String, UUID> applicationFlowMap;
    private final Sequence seqLayerFlowId;
    private final Map<String, ApplicationEntitlement> applicationEntitlementMap;
    private final Map<String, ApplicationDescriptor> entitledApplicationDescriptors;

    LayeredFlowsBuilder(CommonStageContext stageContext, List<ApplicationEntitlement> appEntitlements,
      Map<EntitlementType, ApplicationFlowFactory> applicationFlowFactories) {
      this.request = stageContext.getEntitlementRequest();
      this.seqLayerFlowId = Sequence.withPrefix(stageContext.flowId() + "/ApplicationsInstaller/Level");

      this.applicationFlowMap = MapUtils.emptyIfNull(stageContext.getQueuedApplicationFlows());
      this.applicationEntitlementMap = toHashMap(appEntitlements, ApplicationEntitlement::applicationId, identity());

      var entitledDescriptors = stageContext.getEntitledApplicationDescriptors();
      this.entitledApplicationDescriptors = toHashMap(entitledDescriptors, ApplicationDescriptor::getName, identity());

      this.applicationFlowFactories = applicationFlowFactories;
    }

    List<Stage<? extends StageContext>> build(List<Set<String>> layers) {
      return mapItems(layers, this::prepareLayerStage);
    }

    @SuppressWarnings("java:S1452")
    private Stage<? extends StageContext> prepareLayerStage(Set<String> layerApplicationIds) {
      var layerFlowId = seqLayerFlowId.nextValue();
      var applicationFlows = mapItems(layerApplicationIds, appId -> getPrepareApplicationFlow(appId, layerFlowId));
      return combineApplicationLayerFlows(layerFlowId, applicationFlows);
    }

    private Flow getPrepareApplicationFlow(String applicationId, String layerFlowId) {
      var applicationEntitlement = applicationEntitlementMap.get(applicationId);
      var applicationFlowId = applicationFlowMap.get(applicationId);
      var flowId = layerFlowId + "/" + applicationFlowId;

      var applicationFlowFactory = applicationFlowFactories.get(applicationEntitlement.type());
      var additionalFlowParameters = prepareFlowParameters(applicationFlowId, applicationEntitlement);

      return applicationFlowFactory.createFlow(flowId, request.getExecutionStrategy(), additionalFlowParameters);
    }

    private Map<?, ?> prepareFlowParameters(UUID applicationFlowId, ApplicationEntitlement applicationEntitlement) {
      var descriptor = applicationEntitlement.descriptor();
      var applicationName = descriptor.getName();

      if (applicationEntitlement.type() == UPGRADE) {
        var entitledApplicationDescriptor = requireNonNull(entitledApplicationDescriptors.get(applicationName));
        return Map.of(
          PARAM_APPLICATION_ENTITLEMENT_TYPE, applicationEntitlement.type(),
          PARAM_APPLICATION_ID, descriptor.getId(),
          PARAM_APPLICATION_DESCRIPTOR, descriptor,
          PARAM_ENTITLED_APPLICATION_DESCRIPTOR, entitledApplicationDescriptor,
          PARAM_ENTITLED_APPLICATION_ID, entitledApplicationDescriptor.getId(),
          PARAM_APPLICATION_FLOW_ID, applicationFlowId);
      }

      return Map.of(
        PARAM_APPLICATION_ENTITLEMENT_TYPE, applicationEntitlement.type(),
        PARAM_APPLICATION_ID, descriptor.getId(),
        PARAM_APPLICATION_DESCRIPTOR, descriptor,
        PARAM_APPLICATION_FLOW_ID, applicationFlowId);
    }

    @SuppressWarnings("java:S1452")
    private static Stage<? extends StageContext> combineApplicationLayerFlows(String flowId, List<Flow> flows) {
      return combineStages(flowId, flows);
    }
  }
}
