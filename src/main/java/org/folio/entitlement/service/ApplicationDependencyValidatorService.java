package org.folio.entitlement.service;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.join;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.exception.RequestValidationException.Params;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.security.domain.model.descriptor.InterfaceDescriptor;
import org.folio.security.domain.model.descriptor.InterfaceReference;
import org.folio.security.domain.model.descriptor.ModuleDescriptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ApplicationDependencyValidatorService {

  private static final String SYSTEM_INTERFACE_TYPE = "system";
  private static final String APPLICATION_IDS = "applicationIds";

  private final ApplicationDescriptorTreeLoader applicationTreeLoader;

  public void validateApplications(UUID tenantId, Set<String> applicationIds, String token) {
    if (isEmpty(applicationIds)) {
      throw new RequestValidationException("No application ids provided", APPLICATION_IDS, null);
    }
    log.info("Validating dependencies of applications: appIds = [{}], tenantId = {}",
      () -> join(applicationIds, ", "), () -> tenantId);

    var allApplications = applicationTreeLoader.load(applicationIds, tenantId, token);

    validateDescriptors(allApplications);
  }

  public void validateDescriptors(List<ApplicationDescriptor> descriptors) {
    if (isEmpty(descriptors)) {
      throw new RequestValidationException("No application descriptors provided", "descriptors", null);
    }
    log.info("Validating dependencies between application descriptors: appIds = [{}]",
      () -> descriptors.stream().map(ApplicationDescriptor::getId).collect(Collectors.joining(", ")));

    var required = new HashSet<InterfaceItem>();
    var provided = new HashMap<String, Set<InterfaceItem>>(); // map of: interfaceId ->> set[interfaceItem]
    populateRequiredAndProvidedInterfaces(descriptors, required, provided);

    var missingInterfaces = findMissingDependencies(required, provided);

    if (isNotEmpty(missingInterfaces)) {
      throw new RequestValidationException("Missing dependencies found for the applications",
        toParams(missingInterfaces));
    }
  }

  private Set<InterfaceItem> findMissingDependencies(Set<InterfaceItem> required,
    Map<String, Set<InterfaceItem>> provided) {
    var result = required.stream()
      .filter(not(foundIn(provided)))
      .collect(toSet());

    log.debug("Missing dependencies {}",
      () -> result.isEmpty()
        ? "not found"
        : "found: " + result.stream().map(InterfaceItem::interfaceRefAsString).collect(joining(", ", "[", "]")));

    return result;
  }

  private static Predicate<? super InterfaceItem> foundIn(Map<String, Set<InterfaceItem>> provided) {
    return testing -> {
      var testingRef = testing.interfaceRef();

      var interfaceVariations = provided.get(testingRef.getId());

      return interfaceVariations != null
        && interfaceVariations.stream()
        .map(InterfaceItem::interfaceRef)
        .anyMatch(ref -> ref.isCompatible(testingRef));
    };
  }

  private static Params toParams(Set<InterfaceItem> missing) {
    assert isNotEmpty(missing);

    var missingPerApplication = missing.stream().collect(toMap(
      InterfaceItem::appId,
      InterfaceItem::interfaceRefAsString,
      (collectedInterfaces, newInterface) -> collectedInterfaces + "; " + newInterface
    ));

    Params result = new Params();
    missingPerApplication.forEach(result::add);

    return result;
  }

  private void populateRequiredAndProvidedInterfaces(List<ApplicationDescriptor> descriptors,
    Set<InterfaceItem> required, Map<String, Set<InterfaceItem>> provided) {
    log.debug("Reading required/provided interfaces from the descriptors...");

    for (ApplicationDescriptor descriptor : descriptors) {
      String appId = descriptor.getId();

      for (ModuleDescriptor md : emptyIfNull(descriptor.getModuleDescriptors())) {
        populateRequiredAndProvidedFromModule(appId, md, required, provided);
      }

      for (ModuleDescriptor md : emptyIfNull(descriptor.getUiModuleDescriptors())) {
        populateRequiredAndProvidedFromModule(appId, md, required, provided);
      }
    }

    log.debug("Interface summary: required = {}, provided = [{}]", () -> required,
      () -> provided.values().stream()
        .flatMap(Collection::stream)
        .map(Objects::toString)
        .collect(joining(", ")));
  }

  private void populateRequiredAndProvidedFromModule(String appId, ModuleDescriptor md,
    Set<InterfaceItem> required, Map<String, Set<InterfaceItem>> provided) {
    var req = mapItems(md.getRequires(), createInterfaceItem(appId));
    required.addAll(req);

    log.debug("Required interfaces extracted: appId = {}, moduleId = {}, interfaces = {}", appId, md.getId(), req);

    var prv = toStream(md.getProvides())
      .filter(nonSystemInterface())
      .map(createInterfaceItemFromDescriptor(appId))
      .collect(toSet());

    prv.forEach(accumulateIn(provided));

    log.debug("Provided interfaces extracted: appId = {}, moduleId = {}, interfaces = {}", appId, md.getId(), prv);
  }

  private static Function<InterfaceReference, InterfaceItem> createInterfaceItem(String appId) {
    return interfaceRef -> new InterfaceItem(interfaceRef, appId);
  }

  private static Function<InterfaceDescriptor, InterfaceItem> createInterfaceItemFromDescriptor(String appId) {
    return interfaceDescr -> new InterfaceItem(toRef(interfaceDescr), appId);
  }

  private static InterfaceReference toRef(InterfaceDescriptor provide) {
    return InterfaceReference.of(provide.getId(), provide.getVersion());
  }

  private static Predicate<InterfaceDescriptor> nonSystemInterface() {
    return desc -> !Objects.equals(SYSTEM_INTERFACE_TYPE, desc.getInterfaceType());
  }

  private static Consumer<InterfaceItem> accumulateIn(Map<String, Set<InterfaceItem>> holder) {
    return interfaceItem ->
      holder.computeIfAbsent(interfaceItem.interfaceRef().getId(), s -> new HashSet<>())
        .add(interfaceItem);
  }

  private record InterfaceItem(InterfaceReference interfaceRef, String appId) {

    String interfaceRefAsString() {
      return this.interfaceRef().getId() + " " + this.interfaceRef().getVersion();
    }

    @Override
    public String toString() {
      return String.format("[interface = %s, appId = %s]", interfaceRefAsString(), appId);
    }
  }
}
