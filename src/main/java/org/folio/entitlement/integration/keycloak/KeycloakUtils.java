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

  public static void addPubSubResources(ModuleDescriptor newDescriptor) {
    if (newDescriptor != null && newDescriptor.getId() != null && newDescriptor.getId().startsWith("mod-pubsub")) {
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
      newDescriptor.getProvides().add(interfaceDescriptor);
    }
  }
}
