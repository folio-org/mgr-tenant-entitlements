package org.folio.entitlement.service.flow;

import static java.util.function.Function.identity;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.reverseList;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.integration.folio.ApplicationStageContext.PARAM_APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.integration.folio.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.integration.folio.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.integration.folio.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_DESCRIPTOR;
import static org.folio.entitlement.integration.folio.ApplicationStageContext.PARAM_ENTITLED_APPLICATION_ID;
import static org.folio.entitlement.integration.folio.CommonStageContext.PARAM_REQUEST;
import static org.folio.entitlement.utils.EntitlementServiceUtils.toHashMap;
import static org.folio.entitlement.utils.FlowUtils.combineStages;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.MapUtils;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.folio.CommonStageContext;
import org.folio.entitlement.service.ApplicationInstallationGraph;
import org.folio.flow.api.Flow;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationsFlowFactory {

  private final ApplicationFlowFactoryProvider applicationFlowFactoryProvider;

  /**
   * Creates a {@link Flow} for applications enable, disable, or upgrade operation.
   *
   * @param stageContext - stage context to analyze
   * @return prepared application {@link Flow} object
   */
  public Flow createFlow(StageContext stageContext) {
    var ctx = CommonStageContext.decorate(stageContext);
    var flowId = ctx.flowId();
    var request = ctx.getEntitlementRequest();
    var applicationIds = request.getApplications();
    var loadedDescriptors = ctx.getApplicationDescriptors();

    var requestDescriptors = loadedDescriptors.stream()
      .filter(applicationDescriptor -> applicationIds.contains(applicationDescriptor.getId()))
      .toList();

    var applicationDescriptorsLayers = getApplicationDescriptorsLayers(request.getType(), requestDescriptors);
    var lfp = new LayerFlowProvider(ctx, requestDescriptors);
    var applicationLayerFlows = mapItems(applicationDescriptorsLayers, lfp::prepareLayerStage);
    ctx.clearContext();

    return buildApplicationsFlow(flowId, request, applicationLayerFlows);
  }

  /**
   * Returns factory name.
   *
   * @return factory name as {@link String} object
   */
  public String getName() {
    return this.getClass().getSimpleName();
  }

  private List<Set<String>> getApplicationDescriptorsLayers(EntitlementType type, List<ApplicationDescriptor> list) {
    var applicationGraph = new ApplicationInstallationGraph(list);
    var result = applicationGraph.getInstallationSequence();
    return type == ENTITLE ? result : reverseList(result);
  }

  private static Flow buildApplicationsFlow(String flowId, EntitlementRequest request,
    List<? extends Stage<? extends StageContext>> stages) {
    var builder = Flow.builder()
      .id(flowId + "/ApplicationsFlow")
      .executionStrategy(request.getExecutionStrategy())
      .flowParameter(PARAM_REQUEST, request);

    stages.forEach(builder::stage);

    return builder.build();
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
    private final Map<String, ApplicationDescriptor> applicationDescriptorMap;
    private final Map<String, ApplicationDescriptor> entitledApplicationDescriptors;

    LayerFlowProvider(CommonStageContext stageContext, List<ApplicationDescriptor> applicationDescriptors) {
      var flowId = stageContext.flowId();
      this.request = stageContext.getEntitlementRequest();
      this.applicationFlowMap = MapUtils.emptyIfNull(stageContext.getQueuedApplicationFlows());
      this.seqLayerFlowId = Sequence.withPrefix(flowId + "/ApplicationsInstaller/Level");
      this.applicationDescriptorMap = toHashMap(applicationDescriptors, ApplicationDescriptor::getId, identity());

      var entitledDescriptors = stageContext.getEntitledApplicationDescriptors();
      this.entitledApplicationDescriptors = toHashMap(entitledDescriptors, ApplicationDescriptor::getName, identity());
    }

    Stage<? extends StageContext> prepareLayerStage(Set<String> layerApplicationIds) {
      var layerFlowId = seqLayerFlowId.nextValue();
      var applicationFlows = mapItems(layerApplicationIds, appId -> getPrepareApplicationFlow(appId, layerFlowId));
      return combineApplicationLayerFlows(layerFlowId, applicationFlows);
    }

    private Flow getPrepareApplicationFlow(String applicationId, String layerFlowId) {
      var applicationFlowId = applicationFlowMap.get(applicationId);
      var flowId = layerFlowId + "/" + applicationFlowId;
      var applicationFlowFactory = applicationFlowFactoryProvider.get(request.getType());
      var additionalFlowParameters = prepareFlowParameters(applicationId);

      return applicationFlowFactory.createFlow(flowId, request.getExecutionStrategy(), additionalFlowParameters);
    }

    private Map<?, ?> prepareFlowParameters(String applicationId) {
      var applicationFlowId = applicationFlowMap.get(applicationId);
      var descriptor = applicationDescriptorMap.get(applicationId);
      var applicationName = descriptor.getName();

      var entitledApplicationDescriptor = entitledApplicationDescriptors.get(applicationName);
      if (request.getType() == UPGRADE && entitledApplicationDescriptor != null) {
        return Map.of(
          PARAM_APPLICATION_ID, descriptor.getId(),
          PARAM_APPLICATION_DESCRIPTOR, descriptor,
          PARAM_ENTITLED_APPLICATION_DESCRIPTOR, entitledApplicationDescriptor,
          PARAM_ENTITLED_APPLICATION_ID, entitledApplicationDescriptor.getId(),
          PARAM_APPLICATION_FLOW_ID, applicationFlowId);
      }

      return Map.of(
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
