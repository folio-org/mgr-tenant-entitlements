package org.folio.entitlement.service;

import static java.util.Collections.emptyMap;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;
import static org.folio.entitlement.utils.EntitlementServiceUtils.toHashMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import lombok.Data;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.Module;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.domain.model.ModuleDescriptorHolder;
import org.folio.entitlement.domain.model.ModulesSequence;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.utils.EntitlementServiceUtils;
import org.springframework.stereotype.Component;

@Component
public class ModuleSequenceProvider {

  /**
   * Creates {@link ModulesSequence} object from application stage context, entitlement and module types.
   *
   * @param stageContext - {@link ApplicationStageContext} object
   * @param moduleType - {@link ModuleType} enum value
   * @return created {@link ModulesSequence} object
   */
  public ModulesSequence getSequence(ApplicationStageContext stageContext, ModuleType moduleType) {
    var helper = new ModulesHelper(stageContext, moduleType);
    var entitlementType = stageContext.getEntitlementType();
    return new ModulesSequence(
      getModuleDescriptorHolders(entitlementType, helper),
      getDeprecatedModuleDescriptors(helper));
  }

  private static List<List<ModuleDescriptorHolder>> getModuleDescriptorHolders(EntitlementType type, ModulesHelper mh) {
    var moduleInstallationGraph = new ModuleInstallationGraph(mh.getModuleDescriptors(), type);
    var moduleDescriptorsMap = getModuleDescriptorsMap(mh);
    var moduleInstallationSequence = moduleInstallationGraph.getInstallationSequence();
    return mapItems(moduleInstallationSequence, moduleIds -> mapItems(moduleIds, moduleDescriptorsMap::get));
  }

  private static List<List<ModuleDescriptor>> getDeprecatedModuleDescriptors(ModulesHelper mh) {
    var deprecatedModuleDescriptors = mh.getDeprecatedModuleDescriptors();
    var installationGraph = new ModuleInstallationGraph(deprecatedModuleDescriptors, REVOKE);
    var installedModuleDescriptors = mh.getInstalledModuleDescriptorsById();
    var moduleInstallationSequence = installationGraph.getInstallationSequence();
    return mapItems(moduleInstallationSequence, moduleIds -> mapItems(moduleIds, installedModuleDescriptors::get));
  }

  private static Map<String, ModuleDescriptorHolder> getModuleDescriptorsMap(ModulesHelper mh) {
    var moduleDescriptorsMap = new HashMap<String, ModuleDescriptorHolder>();
    for (var moduleEntry : mh.getModulesByName().entrySet()) {
      var moduleName = moduleEntry.getKey();
      var module = moduleEntry.getValue();
      var moduleDescriptor = mh.getModuleDescriptor(module.getId());
      var installedModuleDescriptor = mh.getInstalledModuleDescriptorByName(moduleName);

      var pair = new ModuleDescriptorHolder(moduleDescriptor, installedModuleDescriptor);
      moduleDescriptorsMap.put(moduleDescriptor.getId(), pair);
    }

    return moduleDescriptorsMap;
  }

  @Data
  public static final class ModulesHelper {

    private final Map<String, Module> modulesByName;
    private final List<ModuleDescriptor> moduleDescriptors;
    private final Map<String, Module> installedModulesByName;
    private final Map<String, ModuleDescriptor> moduleDescriptorsById;
    private final Map<String, ModuleDescriptor> installedModuleDescriptorsById;

    /**
     * Creates {@link ModulesHelper} from {@link ApplicationStageContext} object.
     *
     * @param stageContext - application stage context
     */
    public ModulesHelper(ApplicationStageContext stageContext, ModuleType moduleType) {
      var appDescriptor = stageContext.getApplicationDescriptor();
      var moduleExtractor = getModuleExtractor(moduleType);
      var moduleDescriptorExtractor = getModuleDescExtractor(moduleType);
      this.modulesByName = collectModulesByName(appDescriptor, moduleExtractor);
      this.moduleDescriptors = moduleDescriptorExtractor.apply(appDescriptor);
      this.moduleDescriptorsById = toHashMap(this.moduleDescriptors, ModuleDescriptor::getId);

      var entitledAppDesc = stageContext.getEntitledApplicationDescriptor();
      this.installedModulesByName = collectModulesByName(entitledAppDesc, moduleExtractor);
      this.installedModuleDescriptorsById = groupDescriptorsById(entitledAppDesc, moduleDescriptorExtractor);
    }

    /**
     * Returns {@link ModuleDescriptor} by module id.
     *
     * @param moduleId - module identifier
     * @return {@link ModuleDescriptor} by id
     */
    public ModuleDescriptor getModuleDescriptor(String moduleId) {
      return moduleDescriptorsById.get(moduleId);
    }

    /**
     * Retrieves installed module descriptor by module id and type.
     *
     * @param moduleName - module name
     * @return installed {@link ModuleDescriptor} object, nullable
     */
    public ModuleDescriptor getInstalledModuleDescriptorByName(String moduleName) {
      return Optional.ofNullable(installedModulesByName.get(moduleName))
        .map(Module::getId)
        .map(installedModuleDescriptorsById::get)
        .orElse(null);
    }

    public List<ModuleDescriptor> getDeprecatedModuleDescriptors() {
      return installedModulesByName.entrySet().stream()
        .filter(installedModule -> modulesByName.get(installedModule.getKey()) == null)
        .map(installedModule -> installedModuleDescriptorsById.get(installedModule.getValue().getId()))
        .toList();
    }

    private static Map<String, Module> collectModulesByName(ApplicationDescriptor applicationDescriptor,
      Function<ApplicationDescriptor, List<Module>> moduleExtractor) {
      return Optional.ofNullable(applicationDescriptor)
        .map(moduleExtractor)
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

    private static Function<ApplicationDescriptor, List<Module>> getModuleExtractor(ModuleType type) {
      return type == ModuleType.MODULE
        ? ApplicationDescriptor::getModules
        : ApplicationDescriptor::getUiModules;
    }

    private static Function<ApplicationDescriptor, List<ModuleDescriptor>> getModuleDescExtractor(ModuleType type) {
      return type == ModuleType.MODULE
        ? ApplicationDescriptor::getModuleDescriptors
        : ApplicationDescriptor::getUiModuleDescriptors;
    }
  }
}
