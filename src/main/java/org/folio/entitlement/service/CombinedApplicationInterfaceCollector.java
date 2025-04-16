package org.folio.entitlement.service;

import static java.util.stream.Collectors.joining;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@ConditionalOnProperty(name = "application.validation.interface-integrity.interface-collector.mode",
  havingValue = "combined")
public class CombinedApplicationInterfaceCollector implements ApplicationInterfaceCollector {

  @Override
  public Stream<RequiredProvidedInterfaces> collectRequiredAndProvided(List<ApplicationDescriptor> descriptors) {
    if (isEmpty(descriptors)) {
      return Stream.empty();
    }

    log.debug("Reading required/provided interfaces from the descriptors [combined mode]...");

    var result = toStream(descriptors)
      .map(ApplicationInterfaceCollectorUtils::populateRequiredAndProvidedFromApp)
      .reduce(RequiredProvidedInterfaces.empty(), RequiredProvidedInterfaces::merge);

    log.debug("Interface summary: required = {}, provided = [{}]", result::required,
      () -> result.provided().values().stream()
        .flatMap(Collection::stream)
        .map(Objects::toString)
        .collect(joining(", ")));

    return Stream.of(result);
  }
}
