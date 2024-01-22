package org.folio.entitlement.integration.kafka;

import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.flatMapping;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toCollection;
import static org.apache.commons.collections4.CollectionUtils.isEmpty;
import static org.apache.commons.collections4.ListUtils.emptyIfNull;
import static org.folio.common.utils.CollectionUtils.mapItems;
import static org.folio.common.utils.CollectionUtils.toStream;
import static org.folio.entitlement.integration.kafka.model.ModuleType.MODULE;
import static org.folio.entitlement.integration.kafka.model.ModuleType.UI_MODULE;
import static org.folio.entitlement.integration.kafka.model.ResourceEventType.CREATE;
import static org.folio.entitlement.service.flow.EntitlementFlowConstants.PARAM_TENANT_NAME;
import static org.folio.entitlement.service.stage.StageContextUtils.getApplicationDescriptor;
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
import java.util.Set;
import java.util.stream.Collector;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.Permission;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.common.utils.CollectionUtils;
import org.folio.entitlement.integration.kafka.model.CapabilityEventBody;
import org.folio.entitlement.integration.kafka.model.Endpoint;
import org.folio.entitlement.integration.kafka.model.FolioResource;
import org.folio.entitlement.integration.kafka.model.ModuleType;
import org.folio.entitlement.integration.kafka.model.ResourceEvent;
import org.folio.entitlement.service.stage.DatabaseLoggingStage;
import org.folio.flow.api.StageContext;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class CapabilitiesEventPublisher extends DatabaseLoggingStage {

  private static final String CAPABILITIES_TOPIC = "mgr-tenant-entitlements.capability";
  private static final String CAPABILITY_RESOURCE_NAME = "Capability";

  private final KafkaEventPublisher kafkaEventPublisher;

  @Override
  public void execute(StageContext context) {
    var appDesc = getApplicationDescriptor(context);
    var tenant = context.<String>get(PARAM_TENANT_NAME);
    var appId = appDesc.getId();
    emptyIfNull(appDesc.getModuleDescriptors()).forEach(desc -> sendCapabilityEvent(desc, appId, tenant, MODULE));
    emptyIfNull(appDesc.getUiModuleDescriptors()).forEach(desc -> sendCapabilityEvent(desc, appId, tenant, UI_MODULE));
  }

  private void sendCapabilityEvent(ModuleDescriptor descriptor, String applicationId, String tenant, ModuleType type) {
    var folioResourcesList = getFolioResourcesList(descriptor);
    if (isEmpty(folioResourcesList)) {
      log.debug("FolioResource list is empty: applicationId = {}, moduleId = {}", applicationId, descriptor.getId());
      return;
    }

    var moduleId = descriptor.getId();
    var capabilityEvent = ResourceEvent.<CapabilityEventBody>builder()
      .type(CREATE)
      .tenant(tenant)
      .resourceName(CAPABILITY_RESOURCE_NAME)
      .newValue(CapabilityEventBody.of(moduleId, type, applicationId, folioResourcesList))
      .build();

    kafkaEventPublisher.send(getTenantTopicName(CAPABILITIES_TOPIC, tenant), moduleId, capabilityEvent);
  }

  private static List<FolioResource> getFolioResourcesList(ModuleDescriptor moduleDescriptor) {
    var permissionsByName = groupPermissionsByName(moduleDescriptor);
    var endpointsByPermission = groupEndpointsByPermission(moduleDescriptor);
    var folioResourcesWithEndpointPairs = getFolioResourcesWithEndpoints(permissionsByName, endpointsByPermission);
    var folioResources = new ArrayList<>(folioResourcesWithEndpointPairs.folioResources());
    var visitedPermissionNames = folioResourcesWithEndpointPairs.visitedPermissions();

    for (var permissionEntry : permissionsByName.entrySet()) {
      if (!visitedPermissionNames.contains(permissionEntry.getKey())) {
        folioResources.add(FolioResource.of(permissionEntry.getValue(), null));
      }
    }

    return folioResources;
  }

  private static Map<String, Set<Endpoint>> groupEndpointsByPermission(ModuleDescriptor moduleDescriptor) {
    return toStream(moduleDescriptor.getProvides())
      .filter(not(InterfaceDescriptor::isSystem))
      .map(InterfaceDescriptor::getHandlers)
      .flatMap(CollectionUtils::toStream)
      .map(CapabilitiesEventPublisher::getPermissionEndpointsEntries)
      .flatMap(Collection::stream)
      .collect(groupingBy(Entry::getKey, LinkedHashMap::new,
        mapping(Entry::getValue, flatMapping(List::stream, toLinkedHashSet()))));
  }

  private static List<Entry<String, List<Endpoint>>> getPermissionEndpointsEntries(RoutingEntry handler) {
    var staticPath = handler.getStaticPath();
    var endpoints = mapItems(getMethods(handler), httpMethod -> Endpoint.of(staticPath, httpMethod));
    return mapItems(handler.getPermissionsRequired(), permission -> new SimpleImmutableEntry<>(permission, endpoints));
  }

  private static ResourceHolder getFolioResourcesWithEndpoints(
    Map<String, Permission> permissionsByName, Map<String, Set<Endpoint>> endpointsByPermission) {
    var folioResources = new ArrayList<FolioResource>();
    var visitedPermissions = new HashSet<String>();
    for (var endpointEntry : endpointsByPermission.entrySet()) {
      var permissionName = endpointEntry.getKey();
      var endpoints = endpointEntry.getValue();

      var permission = permissionsByName.get(permissionName);
      if (permission == null) {
        log.warn("Permission value is not found: permissionName = {}", permissionName);
        continue;
      }

      visitedPermissions.add(permissionName);
      folioResources.add(FolioResource.of(permission, new ArrayList<>(endpoints)));
    }

    return new ResourceHolder(folioResources, visitedPermissions);
  }

  private static Map<String, Permission> groupPermissionsByName(ModuleDescriptor moduleDescriptor) {
    var permissionsByNameMap = new LinkedHashMap<String, Permission>();
    for (var permission : emptyIfNull(moduleDescriptor.getPermissionSets())) {
      var permissionName = permission.getPermissionName();
      var existingPermission = permissionsByNameMap.get(permissionName);
      if (existingPermission != null) {
        log.warn("Duplicated permission found: permissionName = {}", permissionName);
        continue;
      }

      permissionsByNameMap.put(permissionName, permission);
    }

    return permissionsByNameMap;
  }

  private static Collector<Endpoint, ?, Set<Endpoint>> toLinkedHashSet() {
    return toCollection(LinkedHashSet::new);
  }

  private record ResourceHolder(List<FolioResource> folioResources, Set<String> visitedPermissions) {}
}
