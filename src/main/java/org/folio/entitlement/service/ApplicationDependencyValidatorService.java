package org.folio.entitlement.service;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.join;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.entitlement.domain.model.EntitlementRequest;
import org.folio.entitlement.domain.model.InterfaceItem;
import org.folio.entitlement.exception.RequestValidationException;
import org.folio.entitlement.exception.RequestValidationException.Params;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;
import org.folio.entitlement.service.ApplicationInterfaceCollector.RequiredProvidedInterfaces;
import org.folio.entitlement.service.stage.ApplicationDescriptorTreeLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ApplicationDependencyValidatorService {

  private static final String APPLICATION_IDS = "applicationIds";

  private final ApplicationDescriptorTreeLoader applicationTreeLoader;
  private final ApplicationInterfaceCollector interfaceCollector;

  public void validateApplications(EntitlementRequest request) {
    var tenantId = request.getTenantId();
    var applicationIds = request.getApplications();
    if (isEmpty(applicationIds)) {
      throw new RequestValidationException("No application ids provided", APPLICATION_IDS, null);
    }

    log.info("Validating dependencies of applications: appIds = [{}], tenantId = {}",
      () -> join(applicationIds, ", "), () -> tenantId);

    var applicationDescriptors = applicationTreeLoader.load(request);

    validateDescriptors(applicationDescriptors);
  }

  public void validateDescriptors(List<ApplicationDescriptor> descriptors) {
    if (isEmpty(descriptors)) {
      throw new RequestValidationException("No application descriptors provided", "descriptors", null);
    }
    log.info("Validating dependencies between application descriptors: appIds = [{}]",
      () -> descriptors.stream().map(ApplicationDescriptor::getId).collect(Collectors.joining(", ")));

    var missingInterfaces = interfaceCollector.collectRequiredAndProvided(descriptors)
      .flatMap(this::findMissingInterfaces)
      .collect(toSet());

    log.debug("Missing interfaces {}",
      () -> missingInterfaces.isEmpty()
        ? "not found"
        : "found: " + missingInterfaces.stream().map(InterfaceItem::toString).collect(joining(", ")));

    if (isNotEmpty(missingInterfaces)) {
      throw new RequestValidationException("Missing interfaces found for the applications",
        toParams(missingInterfaces));
    }
  }

  private Stream<InterfaceItem> findMissingInterfaces(RequiredProvidedInterfaces interfaces) {
    return interfaces.required().stream()
      .filter(not(foundIn(interfaces.provided())));
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
}
