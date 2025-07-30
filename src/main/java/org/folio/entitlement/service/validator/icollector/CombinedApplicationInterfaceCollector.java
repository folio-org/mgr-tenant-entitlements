package org.folio.entitlement.service.validator.icollector;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.service.validator.icollector.ApplicationInterfaceCollectorUtils.getEntitledApplicationIds;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.service.EntitlementCrudService;

@Log4j2
@RequiredArgsConstructor
public class CombinedApplicationInterfaceCollector implements ApplicationInterfaceCollector {

  private final EntitlementCrudService entitlementCrudService;
  private final boolean excludeRequiredInterfacesOfEntitledApps;

  @Override
  public Stream<RequiredProvidedInterfaces> collectRequiredAndProvided(List<ApplicationDescriptor> descriptors,
    UUID tenantId) {
    if (isEmpty(descriptors)) {
      return Stream.empty();
    }

    log.debug("Reading required/provided interfaces from the descriptors [combined mode]...");

    var populateInterfacesFromDescriptor = getPopulateInterfacesMethod(descriptors, tenantId);

    var result = toStream(descriptors)
      .map(populateInterfacesFromDescriptor)
      .reduce(RequiredProvidedInterfaces.empty(), RequiredProvidedInterfaces::merge);

    log.debug("Interface summary: required = {}, provided = [{}]", result::required,
      () -> result.provided().values().stream()
        .flatMap(Collection::stream)
        .map(Objects::toString)
        .collect(joining(", ")));

    return Stream.of(result);
  }

  private Function<ApplicationDescriptor, RequiredProvidedInterfaces> getPopulateInterfacesMethod(
    List<ApplicationDescriptor> descriptors, UUID tenantId) {
    return excludeRequiredInterfacesOfEntitledApps
      ? populateInterfacesDependingOnEntitlement(
          getEntitledApplicationIds(descriptors, tenantId, entitlementCrudService))
      : ApplicationInterfaceCollectorUtils::populateRequiredAndProvidedFromApp;
  }

  private static Function<ApplicationDescriptor, RequiredProvidedInterfaces> populateInterfacesDependingOnEntitlement(
    Set<String> entitledApplicationIds) {
    return descriptor -> entitledApplicationIds.contains(descriptor.getId())
      ? ApplicationInterfaceCollectorUtils.populateProvidedFromApp(descriptor)
      : ApplicationInterfaceCollectorUtils.populateRequiredAndProvidedFromApp(descriptor);
  }
}
