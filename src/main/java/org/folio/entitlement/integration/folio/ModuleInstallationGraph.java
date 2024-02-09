package org.folio.entitlement.integration.folio;

import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableMap;
import static java.util.Objects.requireNonNullElse;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.collections4.CollectionUtils.emptyIfNull;
import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.collections4.CollectionUtils;
import org.folio.common.domain.model.InterfaceReference;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.am.model.ApplicationDescriptor;

public class ModuleInstallationGraph {

  private final List<ModuleDescriptor> modules;
  private final Map<String, Integer> moduleIndices;
  private final Map<InterfaceReference, List<String>> interfacesByModule;
  private final int[][] adjacencyMatrix;

  public ModuleInstallationGraph(ApplicationDescriptor applicationDescriptor) {
    this.modules = requireNonNullElse(applicationDescriptor.getModuleDescriptors(), emptyList());
    var counter = new AtomicInteger();
    this.moduleIndices = modules.stream().collect(toMap(ModuleDescriptor::getId, v -> counter.getAndIncrement()));
    this.interfacesByModule = getInterfacesByModule();
    this.adjacencyMatrix = prepareAdjacencyMatrix();
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
  public List<Set<String>> getModuleInstallationSequence() {
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
        var moduleId = modules.get(j).getId();
        moduleInstallationSequence.computeIfAbsent(cycleCounter, v -> new HashSet<>()).add(moduleId);
      }
    }

    cycleCounter++;

    while (isNotEmpty(remainingIndices)) {
      var currInterationVisitedModuleIndices = new HashSet<Integer>();
      for (var remainingIdx : new ArrayList<>(remainingIndices)) {
        var remainingIndexMatrixRow = adjacencyMatrix[remainingIdx];
        var dependentModuleIndices = getDependentModuleIndices(remainingIndexMatrixRow);
        if (visitedModuleIndices.containsAll(dependentModuleIndices)) {
          currInterationVisitedModuleIndices.add(remainingIdx);
          var moduleId = modules.get(remainingIdx).getId();
          moduleInstallationSequence.computeIfAbsent(cycleCounter, v -> new HashSet<>()).add(moduleId);
          remainingIndices.remove(remainingIdx);
        }
      }

      visitedModuleIndices.addAll(currInterationVisitedModuleIndices);
      cycleCounter++;
    }

    return moduleInstallationSequence.entrySet()
      .stream()
      .sorted(Comparator.comparingInt(Entry::getKey))
      .map(Entry::getValue)
      .filter(CollectionUtils::isNotEmpty)
      .toList();
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
      if (CollectionUtils.isEmpty(requires)) {
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
      .collect(toList());
  }

  private Set<Integer> getDependentModuleIndices(int[] row) {
    var result = new HashSet<Integer>();
    for (int i = 0; i < row.length; i++) {
      if (row[i] != 0) {
        result.add(i);
      }
    }

    return result;
  }
}
