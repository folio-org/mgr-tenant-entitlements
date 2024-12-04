package org.folio.entitlement.integration.kafka;

import static java.util.Collections.emptyList;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.CAPABILITIES_TOPIC;
import static org.folio.entitlement.integration.kafka.KafkaEventUtils.CAPABILITY_RESOURCE_NAME;
import static org.folio.entitlement.utils.RoutingEntryUtils.getMethods;
import static org.folio.integration.kafka.KafkaUtils.getTenantTopicName;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.tuple.Pair;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.Permission;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.common.utils.CollectionUtils;
import org.folio.entitlement.integration.kafka.model.CapabilityEventPayload;
import org.folio.entitlement.integration.kafka.model.Endpoint;
import org.folio.entitlement.integration.kafka.model.FolioResource;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class CapabilitiesModuleEventPublisher extends AbstractModuleEventPublisher<CapabilityEventPayload> {

  @Override
  protected Optional<CapabilityEventPayload> getEventPayload(String appId, ModuleType type, ModuleDescriptor desc) {
    return getCapabilityEventPayload(appId, type, desc);
  }

  @Override
  protected String getTopicName(String tenantName) {
    return getTenantTopicName(CAPABILITIES_TOPIC, tenantName);
  }

  @Override
  protected String getResourceName() {
    return CAPABILITY_RESOURCE_NAME;
  }

  @Override
  protected Optional<Pair<CapabilityEventPayload, CapabilityEventPayload>> getEventPayloadForNotChangedModule(
    String appId, String entitledAppId, ModuleType type, ModuleDescriptor desc, ModuleDescriptor installedDesc) {
    return getEventPayloadForUnchangedModule(appId, entitledAppId, type, desc, installedDesc);
  }

  /**
   * Provides a capability send event if module is not changed during upgrade process.
   *
   * @param applicationId - application identifier as {@link String}
   * @param entitledApplicationId - entitled application identifier as {@link String}
   * @param type - module type as {@link ModuleType} enum value
   * @param descriptor - new module descriptor as {@link ModuleDescriptor}
   * @param installedDescriptor - installed module descriptor as {@link ModuleDescriptor}
   * @return {@link Optional} of {@link Pair} with old and new payloads, empty if event payload not provided
   */
  public static Optional<Pair<CapabilityEventPayload, CapabilityEventPayload>> getEventPayloadForUnchangedModule(
    String applicationId, String entitledApplicationId, ModuleType type, ModuleDescriptor descriptor,
    ModuleDescriptor installedDescriptor) {
    var oldValue = CapabilityEventPayload.of(installedDescriptor.getId(), type, entitledApplicationId, emptyList());
    var newValue = CapabilityEventPayload.of(descriptor.getId(), type, applicationId, emptyList());
    return Optional.of(Pair.of(newValue, oldValue));
  }

  /**
   * Creates {@link Optional} of {@link CapabilityEventPayload} from given parameters.
   *
   * @param applicationId - application identifier
   * @param moduleType - module type
   * @param descriptor - {@link ModuleDescriptor} object, nullable.
   * @return {@link Optional} of {@link CapabilityEventPayload}
   */
  public static Optional<CapabilityEventPayload> getCapabilityEventPayload(String applicationId,
    ModuleType moduleType, ModuleDescriptor descriptor) {
    if (descriptor == null) {
      return Optional.empty();
    }

    var folioResourcesList = getFolioResourcesList(descriptor);
    if (isEmpty(folioResourcesList)) {
      log.debug("FolioResource list is empty: applicationId = {}, moduleId = {}", applicationId, descriptor.getId());
      return Optional.empty();
    }

    var moduleId = descriptor.getId();
    var capabilityEventPayload = CapabilityEventPayload.of(moduleId, moduleType, applicationId, folioResourcesList);
    return Optional.of(capabilityEventPayload);
  }

  private static List<FolioResource> getFolioResourcesList(ModuleDescriptor moduleDescriptor) {
    var moduleId = moduleDescriptor.getId();
    var permsByName = groupPermissionsByName(moduleDescriptor);
    var endpointsByPerm = groupEndpointsByPermission(moduleDescriptor);
    var folioResourcesWithEndpointPairs = getFolioResourcesWithEndpoints(moduleId, permsByName, endpointsByPerm);
    var folioResources = new ArrayList<>(folioResourcesWithEndpointPairs.folioResources());
    var visitedPermissionNames = folioResourcesWithEndpointPairs.visitedPermissions();

    for (var permissionEntry : permsByName.entrySet()) {
      if (!visitedPermissionNames.contains(permissionEntry.getKey())) {
        if (moduleId.startsWith("mod-pubsub") && KafkaEventUtils.isPermissionMappingExist(permissionEntry.getKey())) {
          Endpoint endpoint = Endpoint.of(KafkaEventUtils.getPermissionValueMapping(permissionEntry.getKey())
              .getEndpoint(),
            KafkaEventUtils.getPermissionValueMapping(permissionEntry.getKey()).getMethod());
          folioResources.add(FolioResource.of(permissionEntry.getValue(), List.of(endpoint)));
        } else {
          folioResources.add(FolioResource.of(permissionEntry.getValue(), null));
        }
      }
    }

    return folioResources;
  }

  private static Map<String, Set<Endpoint>> groupEndpointsByPermission(ModuleDescriptor moduleDescriptor) {
    return toStream(moduleDescriptor.getProvides())
      .filter(not(InterfaceDescriptor::isSystem))
      .map(InterfaceDescriptor::getHandlers)
      .flatMap(CollectionUtils::toStream)
      .map(CapabilitiesModuleEventPublisher::getPermissionEndpointsEntries)
      .flatMap(Collection::stream)
      .collect(groupingBy(Entry::getKey, LinkedHashMap::new,
        mapping(Entry::getValue, flatMapping(List::stream, toCollection(LinkedHashSet::new)))));
  }

  private static List<Entry<String, List<Endpoint>>> getPermissionEndpointsEntries(RoutingEntry handler) {
    var staticPath = handler.getStaticPath();
    var endpoints = mapItems(getMethods(handler), httpMethod -> Endpoint.of(staticPath, httpMethod));
    return mapItems(handler.getPermissionsRequired(), permission -> new SimpleImmutableEntry<>(permission, endpoints));
  }

  private static ResourceHolder getFolioResourcesWithEndpoints(String moduleId,
    Map<String, Permission> permissionsByName, Map<String, Set<Endpoint>> endpointsByPermission) {
    var folioResources = new ArrayList<FolioResource>();
    var visitedPermissions = new HashSet<String>();
    for (var endpointEntry : endpointsByPermission.entrySet()) {
      var permissionName = endpointEntry.getKey();
      var endpoints = endpointEntry.getValue();

      var permission = permissionsByName.get(permissionName);
      if (permission == null) {
        log.warn("Permission value is not found and could be define "
          + "in the another module: moduleId = {}, permissionName = {}", moduleId, permissionName);
        continue;
      }

      visitedPermissions.add(permissionName);
      folioResources.add(FolioResource.of(permission, new ArrayList<>(endpoints)));
    }

    return new ResourceHolder(folioResources, visitedPermissions);
  }

  private static Map<String, Permission> groupPermissionsByName(ModuleDescriptor moduleDescriptor) {
    var permissionsByNameMap = new LinkedHashMap<String, Permission>();
    var moduleId = moduleDescriptor.getId();
    for (var permission : emptyIfNull(moduleDescriptor.getPermissionSets())) {
      var permissionName = permission.getPermissionName();
      var existingPermission = permissionsByNameMap.get(permissionName);
      if (existingPermission != null) {
        log.warn("Duplicated permission found: moduleId = {}, permissionName = {}", moduleId, permissionName);
        continue;
      }

      permissionsByNameMap.put(permissionName, permission);
    }

    return permissionsByNameMap;
  }

  private record ResourceHolder(List<FolioResource> folioResources, Set<String> visitedPermissions) {}
}
