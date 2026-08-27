package org.folio.entitlement.integration.keycloak;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.common.domain.model.InterfaceDescriptor;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.common.domain.model.RoutingEntry;
import org.folio.entitlement.integration.kafka.KafkaEventUtils;
import org.folio.entitlement.integration.kafka.model.PermissionMappingValue;

@Log4j2
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class KeycloakUtils {

  public static ModuleDescriptor withPubSubResources(ModuleDescriptor descriptor) {
    if (descriptor == null || descriptor.getId() == null || !descriptor.getId().startsWith("mod-pubsub")) {
      return descriptor;
    }

    InterfaceDescriptor interfaceDescriptor = new InterfaceDescriptor();
    interfaceDescriptor.setId("pubsub-event-handlers");
    interfaceDescriptor.setVersion("1.1");

    ArrayList<RoutingEntry> handlers = new ArrayList<>();
    for (Map.Entry<String, PermissionMappingValue> mapping : KafkaEventUtils.getPermissionMapping().entrySet()) {
      RoutingEntry routingEntry = new RoutingEntry();
      routingEntry.setMethods(List.of(mapping.getValue().getMethod()));
      routingEntry.setPathPattern(mapping.getValue().getEndpoint());
      routingEntry.setPermissionsRequired(List.of(mapping.getKey()));
      handlers.add(routingEntry);
    }
    interfaceDescriptor.setHandlers(handlers);

    var enrichedProvides = new ArrayList<>(descriptor.getProvides());
    enrichedProvides.add(interfaceDescriptor);

    return new ModuleDescriptor()
      .id(descriptor.getId())
      .description(descriptor.getDescription())
      .replaces(descriptor.getReplaces())
      .tags(descriptor.getTags())
      .requires(descriptor.getRequires())
      .provides(enrichedProvides)
      .optional(descriptor.getOptional())
      .filters(descriptor.getFilters())
      .permissionSets(descriptor.getPermissionSets())
      .env(descriptor.getEnv())
      .uiDescriptor(descriptor.getUiDescriptor())
      .launchDescriptor(descriptor.getLaunchDescriptor())
      .user(descriptor.getUser())
      .metadata(descriptor.getMetadata())
      .extensions(descriptor.getExtensions());
  }
}
