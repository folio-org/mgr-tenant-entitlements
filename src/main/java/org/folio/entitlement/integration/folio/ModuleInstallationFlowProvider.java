package org.folio.entitlement.integration.folio;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.reverseList;
import static org.folio.entitlement.domain.dto.EntitlementType.ENTITLE;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.integration.folio.ApplicationStageContext.PARAM_MODULE_DESCRIPTOR;
import static org.folio.entitlement.integration.folio.ApplicationStageContext.PARAM_MODULE_DISCOVERY;
import static org.folio.entitlement.integration.folio.ApplicationStageContext.PARAM_MODULE_ID;

import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.flow.api.Flow;
import org.folio.flow.api.ParallelStage;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;

@RequiredArgsConstructor
public class ModuleInstallationFlowProvider {

  private final FolioModuleInstaller folioModuleInstaller;
  private final FolioModuleUninstaller folioModuleUninstaller;
  private final FolioModuleEventPublisher folioModuleEventPublisher;

  public Flow prepareFlow(StageContext context) {
    var wrapper = new ApplicationStageContext(context);
    var applicationDescriptor = wrapper.getApplicationDescriptor();

    var moduleInstallationGraph = new ModuleInstallationGraph(applicationDescriptor);
    var installationSequence = moduleInstallationGraph.getModuleInstallationSequence();
    var moduleDescriptorsMap = getModuleDescriptorsMap(applicationDescriptor);

    var request = wrapper.getEntitlementRequest();
    var flowId = context.flowId() + "/FolioModule" + capitalize(request.getType().getValue()) + "Flow";
    var flowBuilder = Flow.builder()
      .id(flowId)
      .executionStrategy(request.getExecutionStrategy());

    var resultSequence = request.getType() == REVOKE ? reverseList(installationSequence) : installationSequence;
    for (var i = 0; i < resultSequence.size(); i++) {
      var moduleIds = resultSequence.get(i);
      flowBuilder.stage(prepareStage(flowId, i, moduleIds, moduleDescriptorsMap, wrapper));
    }

    return flowBuilder.build();
  }

  private Stage<StageContext> prepareStage(String flowId, int level, Set<String> moduleIds,
    Map<String, ModuleDescriptor> moduleDescriptors, ApplicationStageContext context) {
    var stageId = flowId + "/Level-" + level;
    var stages = mapItems(moduleIds, id -> getFolioModuleInstaller(stageId, id, moduleDescriptors.get(id), context));
    return stages.size() == 1 ? stages.get(0) : ParallelStage.of(stageId, stages);
  }

  private Flow getFolioModuleInstaller(String flowId, String moduleId, ModuleDescriptor moduleDescriptor,
    ApplicationStageContext context) {
    var moduleDiscovery = context.getModuleDiscoveryData().get(moduleId);
    var requestType = context.getEntitlementRequest().getType();
    return Flow.builder()
      .id(flowId + "/" + moduleId)
      .flowParameter(PARAM_MODULE_ID, moduleId)
      .flowParameter(PARAM_MODULE_DISCOVERY, moduleDiscovery)
      .flowParameter(PARAM_MODULE_DESCRIPTOR, moduleDescriptor)
      .stage(requestType == ENTITLE ? folioModuleInstaller : folioModuleUninstaller)
      .stage(folioModuleEventPublisher)
      .build();
  }

  private static Map<String, ModuleDescriptor> getModuleDescriptorsMap(ApplicationDescriptor descriptor) {
    return descriptor.getModuleDescriptors().stream()
      .collect(toMap(ModuleDescriptor::getId, identity(), (o1, o2) -> o2));
  }
}
