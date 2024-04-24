package org.folio.entitlement.integration.folio;

import static java.util.Collections.emptySet;
import static java.util.Collections.unmodifiableMap;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.apache.commons.collections4.SetUtils.difference;
import static org.folio.common.utils.CollectionUtils.reverseList;
import static org.folio.entitlement.domain.dto.EntitlementType.REVOKE;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.domain.dto.EntitlementType;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;

public class ModuleInstallationGraph {

  private final List<ModuleDescriptor> modules;
  private final Map<String, Integer> moduleIndices;
  private final EntitlementType entitlementType;
  private final Map<InterfaceReference, List<String>> interfacesByModule;
  private final int[][] adjacencyMatrix;

  /**
   * Creates {@link ModuleInstallationGraph} from application descriptor and {@link EntitlementType}.
   *
   * @param applicationDescriptor - application descriptor
   * @param type - entitlement type
   */
  public ModuleInstallationGraph(ApplicationDescriptor applicationDescriptor, EntitlementType type) {
    this.modules = getAllModuleDescriptors(applicationDescriptor);
    this.entitlementType = type;
    var counter = new AtomicInteger();
    this.moduleIndices = modules.stream().collect(toMap(ModuleDescriptor::getId, v -> counter.getAndIncrement()));
    this.interfacesByModule = getInterfacesByModule();
    this.adjacencyMatrix = prepareAdjacencyMatrix();
  }

  /**
   * Returns a sequence as list of lists with module identifiers as values.
   *
   * <p>
   * This code is intended to prepare a sequence for folio-flow-engine that further can be easily transformed to a flow,
   * code can be improved (if possible in future to get rid of n^2 complexity)
   * </p>
   *
   * @return list of lists of module identifiers as installation sequence
   */
  public List<List<String>> getModuleInstallationSequence() {
    var remainingIndices = new HashSet<Integer>();
    for (var i = 0; i < adjacencyMatrix.length; i++) {
      remainingIndices.add(i);
    }

    var moduleInstallationSequence = new HashMap<Integer, Set<String>>();
    var visitedModuleIndices = new HashSet<Integer>();
    var cycleCounter = 0;
    for (var j = 0; j < adjacencyMatrix.length; j++) {
      if (isIndependentModule(j) == 0) {
        remainingIndices.remove(j);
        visitedModuleIndices.add(j);
        moduleInstallationSequence.computeIfAbsent(cycleCounter, v -> new HashSet<>()).add(getModuleId(j));
      }
    }

    cycleCounter++;

    while (isNotEmpty(remainingIndices)) {
      var currIterationVisitedModuleIndices = new HashSet<Integer>();
      var visitedIndicesMap = new HashMap<Integer, Set<Integer>>();
      for (var remainingIdx : new ArrayList<>(remainingIndices)) {
        var remainingIndexMatrixRow = adjacencyMatrix[remainingIdx];
        var dependentModuleIndices = getDependentModuleIndices(remainingIndexMatrixRow);
        visitedIndicesMap.put(remainingIdx, difference(dependentModuleIndices, visitedModuleIndices));
        if (visitedModuleIndices.containsAll(dependentModuleIndices)) {
          currIterationVisitedModuleIndices.add(remainingIdx);
          moduleInstallationSequence.computeIfAbsent(cycleCounter, v -> new HashSet<>()).add(getModuleId(remainingIdx));
          remainingIndices.remove(remainingIdx);
        }
      }

      var unresolvedDependenciesGraph = new UnresolvedDependenciesGraph(visitedIndicesMap);
      var moduleIdsInCyclicDependency = unresolvedDependenciesGraph.getCyclicDependencies();
      for (var moduleIdx : moduleIdsInCyclicDependency) {
        currIterationVisitedModuleIndices.add(moduleIdx);
        moduleInstallationSequence.computeIfAbsent(cycleCounter, v -> new HashSet<>()).add(getModuleId(moduleIdx));
        remainingIndices.remove(moduleIdx);
      }

      visitedModuleIndices.addAll(currIterationVisitedModuleIndices);
      cycleCounter++;
    }

    var installationSequence = finalizeModuleInstallationSequence(moduleInstallationSequence);
    return entitlementType == REVOKE ? reverseList(installationSequence) : installationSequence;
  }

  private static List<ModuleDescriptor> getAllModuleDescriptors(ApplicationDescriptor applicationDescriptor) {
    var resultDescriptors = new ArrayList<ModuleDescriptor>();
    var moduleDescriptors = applicationDescriptor.getModuleDescriptors();
    if (CollectionUtils.isNotEmpty(moduleDescriptors)) {
      resultDescriptors.addAll(moduleDescriptors);
    }

    var uiModuleDescriptors = applicationDescriptor.getUiModuleDescriptors();
    if (CollectionUtils.isNotEmpty(uiModuleDescriptors)) {
      resultDescriptors.addAll(uiModuleDescriptors);
    }

    return resultDescriptors;
  }

  private Map<InterfaceReference, List<String>> getInterfacesByModule() {
    var result = new HashMap<InterfaceReference, List<String>>();
    for (var moduleDescriptor : modules) {
      for (var desc : emptyIfNull(moduleDescriptor.getProvides())) {
        var interfaceReference = InterfaceReference.of(desc.getId(), desc.getVersion());
        result.computeIfAbsent(interfaceReference, v -> new ArrayList<>()).add(moduleDescriptor.getId());
      }
    }
    return unmodifiableMap(result);
  }

  private static List<List<String>> finalizeModuleInstallationSequence(Map<Integer, Set<String>> sequenceMap) {
    return sequenceMap.entrySet()
      .stream()
      .sorted(Comparator.comparingInt(Entry::getKey))
      .map(Entry::getValue)
      .filter(CollectionUtils::isNotEmpty)
      .map(ModuleInstallationGraph::orderedList)
      .toList();
  }

  private String getModuleId(Integer remainingIdx) {
    return modules.get(remainingIdx).getId();
  }

  private int isIndependentModule(int row) {
    var result = 0;
    for (var i = 0; i < adjacencyMatrix.length; i++) {
      result += adjacencyMatrix[row][i];
    }
    return result;
  }

  private int[][] prepareAdjacencyMatrix() {
    var size = modules.size();
    var matrix = new int[size][size];
    for (var i = 0; i < size; i++) {
      var moduleDescriptor = modules.get(i);
      var requires = moduleDescriptor.getRequires();
      if (isEmpty(requires)) {
        continue;
      }

      for (var requiredInterface : requires) {
        var requiredModuleIds = findRequiredModuleIds(moduleDescriptor.getId(), requiredInterface);
        for (var moduleId : requiredModuleIds) {
          var moduleIdx = moduleIndices.get(moduleId);
          matrix[i][moduleIdx] = 1;
        }
      }
    }

    return matrix;
  }

  private List<String> findRequiredModuleIds(String sourceModuleId, InterfaceReference interfaceReference) {
    return interfacesByModule.entrySet().stream()
      .filter(interfaceEntry -> interfaceEntry.getKey().isCompatible(interfaceReference))
      .map(Entry::getValue)
      .flatMap(Collection::stream)
      .filter(moduleId -> !Objects.equals(moduleId, sourceModuleId))
      .toList();
  }

  private Set<Integer> getDependentModuleIndices(int[] row) {
    var result = new HashSet<Integer>();
    for (var i = 0; i < row.length; i++) {
      if (row[i] != 0) {
        result.add(i);
      }
    }

    return result;
  }

  private static List<String> orderedList(Set<String> moduleIds) {
    var moduleIdsList = new ArrayList<>(moduleIds);
    Collections.sort(moduleIdsList);
    return moduleIdsList;
  }

  @RequiredArgsConstructor
  private final class UnresolvedDependenciesGraph {

    private final Map<Integer, Set<Integer>> graph;
    private final Set<Set<Integer>> cyclicDependencySets = new HashSet<>();

    private Set<Integer> getCyclicDependencies() {
      var visited = new boolean[adjacencyMatrix.length];
      var path = new ArrayList<Integer>();

      for (var i = 1; i < visited.length; i++) {
        findCircularDependencies(i, visited, path);
      }

      var foundCircularDependencies = mergeInterceptingSets();
      var result = new HashSet<Integer>();
      for (var circularDependency : foundCircularDependencies) {
        if (!hasOtherDependencies(circularDependency)) {
          result.addAll(circularDependency);
        }
      }

      return result;
    }

    private boolean hasOtherDependencies(Set<Integer> circularDependencySet) {
      var currentDependencies = new HashSet<Integer>();
      for (var idx : circularDependencySet) {
        currentDependencies.addAll(graph.getOrDefault(idx, emptySet()));
      }

      return !currentDependencies.equals(circularDependencySet);
    }

    private void findCircularDependencies(int node, boolean[] visited, List<Integer> path) {
      visited[node] = true;
      path.add(node);

      for (var n : graph.getOrDefault(node, emptySet())) {
        if (visited[n]) {
          collectCircularDependencies(n, path);
          continue;
        }

        findCircularDependencies(n, visited, path);
      }

      path.remove(path.size() - 1);
      visited[node] = false;
    }

    private void collectCircularDependencies(Integer n, List<Integer> path) {
      var index = path.indexOf(n);
      var cycle = new HashSet<Integer>();
      for (var i = index; i < path.size(); i++) {
        cycle.add(path.get(i));
      }

      cyclicDependencySets.add(cycle);
    }

    private List<Set<Integer>> mergeInterceptingSets() {
      var cyclicDependenciesList = new ArrayList<>(cyclicDependencySets);

      for (var i = 0; i < cyclicDependenciesList.size(); i++) {
        var firstSet = cyclicDependenciesList.get(i);
        var iter = cyclicDependenciesList.listIterator(i + 1);
        while (iter.hasNext()) {
          var secondSet = iter.next();

          var intersection = new HashSet<>(firstSet);
          intersection.retainAll(secondSet);

          if (!intersection.isEmpty()) {
            firstSet.addAll(secondSet);
            iter.remove();
          }
        }
      }

      return cyclicDependenciesList;
    }
  }
}
