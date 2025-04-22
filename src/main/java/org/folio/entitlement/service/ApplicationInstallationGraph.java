package org.folio.entitlement.service;

import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections4.MapUtils.emptyIfNull;
import static org.folio.common.utils.CollectionUtils.toStream;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.collections4.ListUtils;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.common.domain.model.Artifact;

@Log4j2
public class ApplicationInstallationGraph {

  private final List<ApplicationDescriptor> descriptors;

  public ApplicationInstallationGraph(List<ApplicationDescriptor> descriptors) {
    this.descriptors = new ArrayList<>(ListUtils.emptyIfNull(descriptors));
  }

  public List<Set<String>> getInstallationSequence() {
    var dependenciesByAppId = toDependenciesPerApplication(descriptors);
    log.debug("Getting installation sequence for applications with dependencies: {}", dependenciesByAppId);

    var result = new ArrayList<Set<String>>();

    var prevLevel = Collections.<String>emptySet();
    while (!dependenciesByAppId.isEmpty()) {
      var currentLevel = new LinkedHashSet<String>();

      iterMap(dependenciesByAppId, collectCurrentLevelApps(currentLevel, prevLevel));
      log.debug("Current level with #{} collected: {}", result.size(), currentLevel);

      if (isNotEmpty(currentLevel)) {
        result.add(currentLevel);
      } else if (!dependenciesByAppId.isEmpty()) {
        // unexpected state of remaining dependency map: the map contains applications, but they all have dependencies
        // and none of them can be considered as an independent one at the current level
        throw new IllegalArgumentException("No more independent applications can be found among the remaining ones: "
          + dependenciesByAppId);
      }

      prevLevel = currentLevel;
    }

    return result;
  }

  private static BiConsumer<Entry<String, Set<String>>, Iterator<Entry<String, Set<String>>>> collectCurrentLevelApps(
    Set<String> currentLevel, Set<String> prevLevel) {
    return (entry, itr) -> {
      var appId = entry.getKey();
      var dependencies = entry.getValue();
      log.debug("Examining application: appId = {}, dependencies = {}", appId, dependencies);

      dependencies.removeAll(prevLevel); // clean up dependencies found during previous iteration

      if (isEmpty(dependencies)) {
        // if there are no dependencies it means the app has become independent and can be included into
        // the current level of applications

        currentLevel.add(appId);
        log.debug("Application added to the current level of installation: {}", appId);

        itr.remove(); // remove the app from the remaining application map after its inclusion into the result
      }
    };
  }

  private static Map<String, Set<String>> toDependenciesPerApplication(List<ApplicationDescriptor> descriptors) {
    var appIdByName = toStream(descriptors).collect(toMap(Artifact::getName, Artifact::getId));

    // map each app dependencies to those that are present in the given application descriptor list.
    // matching between app dependency and potential candidates is done ONLY by application name,
    // version is omitted (it's assumed that the version has been checked during interface integrity validation)
    // if no application matches then the dependency list of the current application is considered to be empty.
    return toStream(descriptors).collect(toMap(Artifact::getId, extractDependencies(appIdByName)));
  }

  private static Function<ApplicationDescriptor, Set<String>> extractDependencies(Map<String, String> appIdByName) {
    return descriptor -> toStream(descriptor.getDependencies())
      .map(dependency -> appIdByName.getOrDefault(dependency.getName(), null))
      .filter(Objects::nonNull)
      .collect(Collectors.toSet());
  }

  private static <K, V> void iterMap(Map<K, V> map, BiConsumer<Entry<K, V>, Iterator<Entry<K, V>>> consumer) {
    for (Iterator<Entry<K, V>> itr = emptyIfNull(map).entrySet().iterator(); itr.hasNext(); ) {
      var entry = itr.next();
      consumer.accept(entry, itr);
    }
  }
}
