package org.folio.entitlement.integration.folio.flow;

import static java.util.Collections.emptyMap;
import static org.apache.commons.lang3.StringUtils.capitalize;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_FLOW_ID;
import static org.folio.entitlement.domain.model.ApplicationStageContext.PARAM_APPLICATION_ID;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_INSTALLED_MODULE_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_DESCRIPTOR;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_DISCOVERY;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_ID;
import static org.folio.entitlement.domain.model.ModuleStageContext.PARAM_MODULE_TYPE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.MODULE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;
import static org.folio.entitlement.utils.EntitlementServiceUtils.toHashMap;
import static org.folio.entitlement.utils.EntitlementServiceUtils.toUnmodifiableMap;
import static org.folio.entitlement.utils.FlowUtils.combineStages;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.domain.model.Sequence;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.integration.am.model.Module;
import org.folio.entitlement.integration.folio.ModuleInstallationGraph;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.service.flow.ModuleFlowFactory;
import org.folio.entitlement.service.flow.ModulesFlowProvider;
import org.folio.entitlement.utils.EntitlementServiceUtils;
import org.folio.entitlement.utils.SemverUtils;
import org.folio.flow.api.Flow;
import org.folio.flow.api.NoOpStage;
import org.folio.flow.api.Stage;
import org.folio.flow.api.StageContext;

public class FolioModulesFlowProvider implements ModulesFlowProvider {

  private final Map<EntitlementType, ModuleFlowFactory> moduleFlowFactories;

  /**
   * Creates {@link FolioModulesFlowProvider} from list of {@link ModuleFlowFactory} beans.
   *
   * @param moduleFlowFactories - {@link ModuleFlowFactory} beans
   */
  public FolioModulesFlowProvider(List<ModuleFlowFactory> moduleFlowFactories) {
    this.moduleFlowFactories = toUnmodifiableMap(moduleFlowFactories, ModuleFlowFactory::getEntitlementType);
  }

  @Override
  public Flow createFlow(StageContext stageContext) {
    var context = new ApplicationStageContext(stageContext);
    var appDescriptor = context.getApplicationDescriptor();

    var request = context.getEntitlementRequest();
    var type = capitalize(request.getType().getValue());
    var flowId = context.flowId() + "/FolioModules" + capitalize(type) + "Flow";
    var sequence = Sequence.withPrefix(flowId + "/Level-");
    var helper = new FlowProviderHelper(context);
    var moduleInstallationGraph = new ModuleInstallationGraph(appDescriptor, request.getType());
    var flowBuilder = Flow.builder().id(flowId).executionStrategy(request.getExecutionStrategy());

    var installationSequence = moduleInstallationGraph.getModuleInstallationSequence();
    for (var moduleIds : installationSequence) {
      var stageId = sequence.nextValue();
      var stages = moduleIds.stream()
        .filter(helper::isVersionChanged)
        .map(id -> getModuleFlow(stageId, id, context, helper))
        .toList();

      flowBuilder.stage(combineStages(stageId, stages));
    }

    var entitledAppDescriptor = context.getEntitledApplicationDescriptor();
    if (entitledAppDescriptor != null) {
      getDeprecatedModulesFlows(flowId, helper, context).forEach(flowBuilder::stage);
    }

    return flowBuilder.build();
  }

  private Flow getModuleFlow(String flowId, String moduleId, ApplicationStageContext ctx, FlowProviderHelper helper) {
    var request = ctx.getEntitlementRequest();
    var moduleFlowFactory = Objects.requireNonNull(moduleFlowFactories.get(request.getType()));
    var descriptor = helper.getModuleDescriptor(moduleId);
    if (descriptor != null || helper.getInstalledModuleDescriptor(moduleId, MODULE) != null) {
      var flowParameters = getModuleFlowParameters(moduleId, descriptor, ctx, helper);
      var moduleFlowId = flowId + "/" + flowParameters.get(PARAM_MODULE_ID);
      return moduleFlowFactory.createModuleFlow(moduleFlowId, request.getExecutionStrategy(), flowParameters);
    }

    var uiDescriptor = helper.getUiModuleDescriptor(moduleId);
    var uiFlowParameters = getModuleFlowParameters(moduleId, uiDescriptor, ctx, helper, UI_MODULE);
    var moduleFlowId = flowId + "/" + uiFlowParameters.get(PARAM_MODULE_ID);
    return moduleFlowFactory.createUiModuleFlow(moduleFlowId, request.getExecutionStrategy(), uiFlowParameters);
  }

  private List<? extends Stage<? extends StageContext>> getDeprecatedModulesFlows(String prefix,
    FlowProviderHelper helper, ApplicationStageContext context) {
    var moduleRevokeGraph = new ModuleInstallationGraph(context.getEntitledApplicationDescriptor(), REVOKE);
    var sequence = Sequence.withPrefix(prefix + "/Deprecated/Level-");
    return moduleRevokeGraph.getModuleInstallationSequence().stream()
      .map(moduleIds -> getDeprecatedModulesFlow(moduleIds, sequence, helper, context))
      .filter(stage -> !NoOpStage.getInstance().equals(stage))
      .toList();
  }

  private Stage<? extends StageContext> getDeprecatedModulesFlow(List<String> moduleIds, Sequence sequence,
    FlowProviderHelper helper, ApplicationStageContext context) {
    var stageId = sequence.nextValue();
    var stages = moduleIds.stream()
      .filter(helper::isDeprecatedModule)
      .map(moduleId -> getModuleFlow(stageId, moduleId, context, helper))
      .toList();

    return combineStages(stageId, stages);
  }

  private static Map<String, Object> getModuleFlowParameters(String moduleId, ModuleDescriptor descriptor,
    ApplicationStageContext context, FlowProviderHelper helper) {
    var flowParameters = getModuleFlowParameters(moduleId, descriptor, context, helper, MODULE);
    var moduleDiscovery = context.getModuleDiscovery(moduleId);
    flowParameters.put(PARAM_MODULE_DISCOVERY, moduleDiscovery);
    return flowParameters;
  }

  private static Map<String, Object> getModuleFlowParameters(String moduleId, ModuleDescriptor descriptor,
    ApplicationStageContext context, FlowProviderHelper helper, ModuleType moduleType) {
    var flowParameters = new HashMap<String, Object>();
    flowParameters.put(PARAM_MODULE_TYPE, moduleType);
    flowParameters.put(PARAM_APPLICATION_ID, context.getApplicationId());
    flowParameters.put(PARAM_APPLICATION_FLOW_ID, context.getCurrentFlowId());

    if (descriptor != null) {
      flowParameters.put(PARAM_MODULE_DESCRIPTOR, descriptor);
      flowParameters.put(PARAM_MODULE_ID, descriptor.getId());
    }

    var installedDescriptor = helper.getInstalledModuleDescriptor(moduleId, moduleType);
    if (installedDescriptor != null) {
      flowParameters.put(PARAM_INSTALLED_MODULE_DESCRIPTOR, installedDescriptor);
      flowParameters.putIfAbsent(PARAM_MODULE_ID, installedDescriptor.getId());
    }

    return flowParameters;
  }

  private static final class FlowProviderHelper {

    private final Map<String, Module> modulesByName;
    private final Map<String, Module> installedModulesByName;
    private final Map<String, ModuleDescriptor> moduleDescriptors;
    private final Map<String, ModuleDescriptor> uiModuleDescriptors;
    private final Map<String, ModuleDescriptor> installedModules;
    private final Map<String, ModuleDescriptor> installedUiModules;

    FlowProviderHelper(ApplicationStageContext stageContext) {
      var appDescriptor = stageContext.getApplicationDescriptor();
      this.modulesByName = collectModulesByName(appDescriptor);
      this.moduleDescriptors = toHashMap(appDescriptor.getModuleDescriptors(), ModuleDescriptor::getId);
      this.uiModuleDescriptors = toHashMap(appDescriptor.getUiModuleDescriptors(), ModuleDescriptor::getId);

      var entitledAppDesc = stageContext.getEntitledApplicationDescriptor();
      this.installedModulesByName = collectModulesByName(entitledAppDesc);
      this.installedModules = groupDescriptorsById(entitledAppDesc, ApplicationDescriptor::getModuleDescriptors);
      this.installedUiModules = groupDescriptorsById(entitledAppDesc, ApplicationDescriptor::getUiModuleDescriptors);
    }

    public ModuleDescriptor getModuleDescriptor(String moduleId) {
      return moduleDescriptors.get(moduleId);
    }

    public ModuleDescriptor getUiModuleDescriptor(String moduleId) {
      return uiModuleDescriptors.get(moduleId);
    }

    public ModuleDescriptor getInstalledModuleDescriptor(String moduleId, ModuleType moduleType) {
      return Optional.ofNullable(installedModulesByName.get(SemverUtils.getName(moduleId)))
        .map(Module::getId)
        .map(id -> moduleType == MODULE ? installedModules.get(id) : installedUiModules.get(id))
        .orElse(null);
    }

    public boolean isVersionChanged(String moduleId) {
      var moduleName = SemverUtils.getName(moduleId);
      var installedModule = installedModulesByName.get(moduleName);
      if (installedModule == null) {
        return true;
      }

      var module = modulesByName.get(moduleName);
      return !Objects.equals(installedModule.getId(), module.getId());
    }

    boolean isDeprecatedModule(String moduleId) {
      var moduleName = SemverUtils.getName(moduleId);
      return !modulesByName.containsKey(moduleName);
    }

    private static Map<String, Module> collectModulesByName(ApplicationDescriptor applicationDescriptor) {
      return Optional.ofNullable(applicationDescriptor)
        .map(desc -> Stream.concat(toStream(desc.getModules()), toStream(desc.getUiModules())).toList())
        .map(EntitlementServiceUtils::groupModulesByNames)
        .orElse(emptyMap());
    }

    private static Map<String, ModuleDescriptor> groupDescriptorsById(ApplicationDescriptor descriptor,
      Function<ApplicationDescriptor, List<ModuleDescriptor>> extractor) {
      return Optional.ofNullable(descriptor)
        .map(extractor)
        .map(descriptors -> toHashMap(descriptors, ModuleDescriptor::getId))
        .orElse(emptyMap());
    }
  }
}
