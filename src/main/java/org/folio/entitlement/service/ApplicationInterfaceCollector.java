package org.folio.entitlement.service;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.folio.common.domain.model.ApplicationDescriptor;
import org.folio.entitlement.domain.model.InterfaceItem;

public interface ApplicationInterfaceCollector {

  /**
   * Collects required and provided interfaces from the given application descriptors.
   *
   * @param descriptors the list of application descriptors
   * @return a stream of {@link RequiredProvidedInterfaces} containing required and provided interfaces
   */
  Stream<RequiredProvidedInterfaces> collectRequiredAndProvided(List<ApplicationDescriptor> descriptors);

  record RequiredProvidedInterfaces(Set<InterfaceItem> required, Map<String, Set<InterfaceItem>> provided) {

    private static final RequiredProvidedInterfaces EMPTY = new RequiredProvidedInterfaces(null, null);

    public RequiredProvidedInterfaces {
      if (CollectionUtils.isEmpty(required)) {
        required = emptySet();
      } else {
        required = Set.copyOf(required);
      }

      if (MapUtils.isEmpty(provided)) {
        provided = emptyMap();
      } else {
        provided = Map.copyOf(provided);
      }
    }

    public static RequiredProvidedInterfaces empty() {
      return EMPTY;
    }

    public RequiredProvidedInterfaces merge(RequiredProvidedInterfaces other) {
      if (other == null) {
        return this;
      }

      var mergedRequired = new HashSet<>(required);
      mergedRequired.addAll(other.required);

      var mergedProvided = new HashMap<String, Set<InterfaceItem>>();
      provided.forEach((id, interfaceItems) -> mergedProvided.put(id, new HashSet<>(interfaceItems)));

      other.provided.forEach((id, interfaceItems) -> mergedProvided.merge(id, interfaceItems, (v1, v2) -> {
        v1.addAll(v2);
        return v1;
      }));

      return new RequiredProvidedInterfaces(mergedRequired, mergedProvided);
    }
  }
}
