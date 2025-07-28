package org.folio.entitlement.service.validator;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toSet;
import static org.folio.common.utils.CollectionUtils.mapItemsToSet;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.service.validator.ApplicationInterfaceCollector.RequiredProvidedInterfaces.empty;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.model.InterfaceItem;
import org.folio.entitlement.service.validator.ApplicationInterfaceCollector.RequiredProvidedInterfaces;

@Log4j2
@UtilityClass
class ApplicationInterfaceCollectorUtils {

  private static final String SYSTEM_INTERFACE_TYPE = "system";

  static RequiredProvidedInterfaces populateRequiredAndProvidedFromApp(ApplicationDescriptor descriptor) {
    return populateFromApp(descriptor, populateRequiredAndProvidedFromModule());
  }

  static RequiredProvidedInterfaces populateProvidedFromApp(ApplicationDescriptor descriptor) {
    return populateFromApp(descriptor, populateProvidedFromModule());
  }

  private static RequiredProvidedInterfaces populateFromApp(ApplicationDescriptor descriptor,
    ModuleInterfaces populateFromModule) {
    String appId = descriptor.getId();

    var rpModules = toStream(descriptor.getModuleDescriptors())
      .map(md -> populateFromModule.apply(appId, md))
      .reduce(empty(), RequiredProvidedInterfaces::merge);

    var rpUiModules = toStream(descriptor.getUiModuleDescriptors())
      .map(uiMd -> populateFromModule.apply(appId, uiMd))
      .reduce(empty(), RequiredProvidedInterfaces::merge);

    return rpModules.merge(rpUiModules);
  }

  private static ModuleInterfaces populateRequiredAndProvidedFromModule() {
    return (appId, md) -> populateRequiredFromModule().apply(appId, md)
      .merge(populateProvidedFromModule().apply(appId, md));
  }

  private static ModuleInterfaces populateRequiredFromModule() {
    return (appId, md) -> {
      var required = mapItemsToSet(md.getRequires(), createInterfaceItem(appId));

      log.debug("Required interfaces extracted: appId = {}, moduleId = {}, interfaces = {}", appId, md.getId(),
        required);

      return new RequiredProvidedInterfaces(required, null);
    };
  }

  private static ModuleInterfaces populateProvidedFromModule() {
    return (appId, md) -> {
      var provided = toStream(md.getProvides())
        .filter(nonSystemInterface())
        .map(createInterfaceItemFromDescriptor(appId))
        .collect(groupingBy(
          interfaceItem -> interfaceItem.interfaceRef().getId(),
          toSet())); // map of: interfaceId ->> set[interfaceItem]

      log.debug("Provided interfaces extracted: appId = {}, moduleId = {}, interfaces = {}", appId, md.getId(),
        provided);

      return new RequiredProvidedInterfaces(null, provided);
    };
  }

  private static Function<InterfaceReference, InterfaceItem> createInterfaceItem(String appId) {
    return interfaceRef -> new InterfaceItem(interfaceRef, appId);
  }

  private static Predicate<InterfaceDescriptor> nonSystemInterface() {
    return desc -> !Objects.equals(SYSTEM_INTERFACE_TYPE, desc.getInterfaceType());
  }

  private static Function<InterfaceDescriptor, InterfaceItem> createInterfaceItemFromDescriptor(String appId) {
    return interfaceDescr -> new InterfaceItem(toRef(interfaceDescr), appId);
  }

  private static InterfaceReference toRef(InterfaceDescriptor provide) {
    return InterfaceReference.of(provide.getId(), provide.getVersion());
  }

  private interface ModuleInterfaces extends BiFunction<String, ModuleDescriptor, RequiredProvidedInterfaces> {}
}
