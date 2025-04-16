package org.folio.entitlement.service;

import static java.util.Collections.emptySet;
import static java.util.function.UnaryOperator.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Stream.empty;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.service.ApplicationInterfaceCollectorUtils.populateRequiredAndProvidedFromApp;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@ConditionalOnProperty(name = "application.validation.interface-integrity.interface-collector.mode",
  havingValue = "scoped", matchIfMissing = true)
public class ScopedApplicationInterfaceCollector implements ApplicationInterfaceCollector {

  @Override
  public Stream<RequiredProvidedInterfaces> collectRequiredAndProvided(List<ApplicationDescriptor> descriptors) {
    if (isEmpty(descriptors)) {
      return empty();
    }

    log.debug("Reading required/provided interfaces from the descriptors [scoped mode]...");

    var dependencyResolver = new ApplicationDependencyResolver(descriptors);

    return toStream(descriptors)
      .map(descriptor -> combineApplicationWithDependencies(descriptor, dependencyResolver))
      .map(this::populateRequiredAndProvided)
      .peek(reqProv ->
        log.debug("Interface summary: required = {}, provided = [{}]", reqProv::required,
          () -> reqProv.provided().values().stream()
            .flatMap(Collection::stream)
            .map(Objects::toString)
            .collect(joining(", "))));
  }

  private RequiredProvidedInterfaces populateRequiredAndProvided(ApplicationWithDependencies appWithDependencies) {
    var appRequiredProvided = populateRequiredAndProvidedFromApp(appWithDependencies.application());

    var depProvided = toStream(appWithDependencies.dependencies())
      .map(ApplicationInterfaceCollectorUtils::populateProvidedFromApp)
      .reduce(RequiredProvidedInterfaces.empty(), RequiredProvidedInterfaces::merge);

    return appRequiredProvided.merge(depProvided);
  }

  private ApplicationWithDependencies combineApplicationWithDependencies(ApplicationDescriptor application,
    ApplicationDependencyResolver dependencyResolver) {
    var dependencies = dependencyResolver.getAllDependencies(application);

    return new ApplicationWithDependencies(application, dependencies);
  }

  private record ApplicationWithDependencies(ApplicationDescriptor application,
     Set<ApplicationDescriptor> dependencies) {
  }

  static final class ApplicationDependencyResolver {

    private final Map<String, ApplicationDescriptor> applicationsByName;
    private final Map<String, Set<ApplicationDescriptor>> cache = new HashMap<>();

    ApplicationDependencyResolver(List<ApplicationDescriptor> allApplications) {
      if (isEmpty(allApplications)) {
        throw new IllegalArgumentException("No applications provided");
      }

      applicationsByName = allApplications.stream().collect(toMap(ApplicationDescriptor::getName, identity()));
    }

    Set<ApplicationDescriptor> getAllDependencies(ApplicationDescriptor application) {
      log.debug("Resolving dependencies for application: {}", application.getId());

      var result = resolveDependencies(application, new ArrayDeque<>());

      log.debug("Resolved dependencies: application = {}, dependencies = {}",
        application::getId,
        () -> toStream(result).map(ApplicationDescriptor::getId).collect(joining(", ", "[", "]")));

      return result;
    }

    private Set<ApplicationDescriptor> resolveDependencies(ApplicationDescriptor application, Deque<String> visited) {
      var appName = application.getName();
      if (visited.contains(appName)) {
        throw new IllegalArgumentException("Circular application dependency detected for: " + appName
          + ". Chain is: " + appName + " <- "
          + visited.stream().reduce((first, second) -> first + " <- " + second).orElse(""));
      }

      if (isEmpty(application.getDependencies())) {
        log.trace("Application has no dependencies: {}. Returning empty set", application::getId);
        return emptySet();
      }

      if (cache.containsKey(appName)) {
        log.trace("Dependencies for application already resolved: {}. Returning cached value", application::getId);
        return cache.get(appName);
      }

      visited.push(appName);

      var result = new HashSet<ApplicationDescriptor>();
      for (var dependency : application.getDependencies()) {
        var dependencyName = dependency.getName();

        var dependencyDescriptor = applicationsByName.get(dependencyName);
        if (dependencyDescriptor == null) {
          throw new IllegalStateException("Application descriptor not found for: " + dependencyName);
        }

        result.add(dependencyDescriptor);

        result.addAll(resolveDependencies(dependencyDescriptor, visited));
      }

      visited.pop();
      cache.put(appName, result);
      log.trace("Saving resolved dependencies in the cache: application = {}, dependencies = {}",
        application::getId,
        () -> toStream(result).map(ApplicationDescriptor::getId).collect(joining(", ", "[", "]")));

      return result;
    }
  }
}
