package org.folio.entitlement.service;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.mapItemsToSet;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.dto.Entitlement;
import org.folio.entitlement.service.configuration.ApplicationInterfaceCollectorProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "application.validation.interface-integrity.interface-collector.mode",
  havingValue = "combined")
public class CombinedApplicationInterfaceCollector implements ApplicationInterfaceCollector {

  private final EntitlementCrudService entitlementCrudService;
  private final ApplicationInterfaceCollectorProperties collectorProperties;

  @Override
  public Stream<RequiredProvidedInterfaces> collectRequiredAndProvided(List<ApplicationDescriptor> descriptors,
    UUID tenantId) {
    if (isEmpty(descriptors)) {
      return Stream.empty();
    }

    log.debug("Reading required/provided interfaces from the descriptors [combined mode]...");
    var entitledApplicationIds = getEntitledApplicationIds(descriptors, tenantId);

    var result = toStream(descriptors)
      .map(descriptor -> populateInterfaces(descriptor, entitledApplicationIds))
      .reduce(RequiredProvidedInterfaces.empty(), RequiredProvidedInterfaces::merge);

    log.debug("Interface summary: required = {}, provided = [{}]", result::required,
      () -> result.provided().values().stream()
        .flatMap(Collection::stream)
        .map(Objects::toString)
        .collect(joining(", ")));

    return Stream.of(result);
  }

  private Set<String> getEntitledApplicationIds(List<ApplicationDescriptor> descriptors, UUID tenantId) {
    var entitlements = entitlementCrudService.findByApplicationIds(tenantId,
      mapItems(descriptors, ApplicationDescriptor::getId));
    return mapItemsToSet(entitlements, Entitlement::getApplicationId);
  }

  private RequiredProvidedInterfaces populateInterfaces(ApplicationDescriptor descriptor,
    Set<String> entitledApplicationIds) {
    return (collectorProperties.getRequired().isExcludeEntitled()
      && entitledApplicationIds.contains(descriptor.getId()))
      ? ApplicationInterfaceCollectorUtils.populateProvidedFromApp(descriptor)
      : ApplicationInterfaceCollectorUtils.populateRequiredAndProvidedFromApp(descriptor);
  }
}
