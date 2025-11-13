package org.folio.entitlement.integration.okapi.flow;

import static java.util.Objects.requireNonNull;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.domain.dto.EntitlementType.UPGRADE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.MODULE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_DEPRECATED_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_DEPRECATED_UI_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_MODULE_DESCRIPTOR_HOLDERS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_MODULE_ENTITLEMENT_TYPE;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_UI_MODULE_DESCRIPTORS;
import static org.folio.entitlement.integration.okapi.model.OkapiStageContext.PARAM_UI_MODULE_DESCRIPTOR_HOLDERS;
import static org.folio.entitlement.utils.EntitlementServiceUtils.toUnmodifiableMap;

import java.util.List;
import java.util.Map;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.utils.CollectionUtils;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.domain.model.ApplicationStageContext;
import org.folio.entitlement.domain.model.ModuleDescriptorHolder;
import org.folio.entitlement.service.ModuleSequenceProvider;
import org.folio.entitlement.service.flow.ModulesFlowProvider;
import org.folio.flow.api.Flow;
import org.folio.flow.api.StageContext;

public class OkapiModulesFlowProvider implements ModulesFlowProvider {

  private final ModuleSequenceProvider moduleSequenceProvider;
  private final Map<EntitlementType, OkapiModulesFlowFactory> okapiFlowFactories;

  /**
   * Creates {@link OkapiModulesFlowProvider}.
   *
   * @param factories - list with {@link OkapiModulesFlowFactory} beans
   */
  public OkapiModulesFlowProvider(ModuleSequenceProvider sequenceProvider, List<OkapiModulesFlowFactory> factories) {
    this.moduleSequenceProvider = sequenceProvider;
    this.okapiFlowFactories = toUnmodifiableMap(factories, OkapiModulesFlowFactory::getEntitlementType);
  }

  @Override
  public Flow createFlow(StageContext context) {
    var ctx = ApplicationStageContext.decorate(context);
    var flowParameters = getFlowParameters(ctx);

    var factory = requireNonNull(okapiFlowFactories.get(ctx.getEntitlementType()));
    return factory.createFlow(ctx, flowParameters);
  }

  private Map<String, Object> getFlowParameters(ApplicationStageContext stageContext) {
    var descriptorSequence = moduleSequenceProvider.getSequence(stageContext, MODULE);
    var uiDescriptorSequence = moduleSequenceProvider.getSequence(stageContext, UI_MODULE);

    if (stageContext.getEntitlementType() == UPGRADE) {
      return Map.of(
        PARAM_MODULE_ENTITLEMENT_TYPE, stageContext.getEntitlementType(),
        PARAM_MODULE_DESCRIPTOR_HOLDERS, toFlatList(descriptorSequence.moduleDescriptors()),
        PARAM_DEPRECATED_MODULE_DESCRIPTORS, toFlatList(descriptorSequence.deprecatedModuleDescriptors()),
        PARAM_UI_MODULE_DESCRIPTOR_HOLDERS, toFlatList(uiDescriptorSequence.moduleDescriptors()),
        PARAM_DEPRECATED_UI_MODULE_DESCRIPTORS, toFlatList(uiDescriptorSequence.deprecatedModuleDescriptors()));
    }

    return Map.of(
      PARAM_MODULE_ENTITLEMENT_TYPE, stageContext.getEntitlementType(),
      PARAM_MODULE_DESCRIPTORS, getModuleDescriptors(descriptorSequence.moduleDescriptors()),
      PARAM_UI_MODULE_DESCRIPTORS, getModuleDescriptors(uiDescriptorSequence.moduleDescriptors()));
  }

  private static <T> List<T> toFlatList(List<List<T>> sequence) {
    return toStream(sequence)
      .flatMap(CollectionUtils::toStream)
      .toList();
  }

  private static List<ModuleDescriptor> getModuleDescriptors(List<List<ModuleDescriptorHolder>> holders) {
    return toStream(holders)
      .flatMap(CollectionUtils::toStream)
      .map(ModuleDescriptorHolder::moduleDescriptor)
      .toList();
  }
}
