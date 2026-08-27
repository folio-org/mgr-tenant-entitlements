package org.folio.entitlement.integration.keycloak;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakUtilsTest {

  @Test
  void withPubSubResources_returnsCopyWithHandlers() {
    ModuleDescriptor original = new ModuleDescriptor();
    original.setId("mod-pubsub-1.1.0");
    original.setProvides(new ArrayList<>());

    ModuleDescriptor result = KeycloakUtils.withPubSubResources(original);

    assertEquals(1, result.getProvides().size());
    assertEquals(12, result.getProvides().getFirst().getHandlers().size());
    assertEquals(0, original.getProvides().size(), "Original descriptor must not be mutated");
  }

  @Test
  void withPubSubResources_returnsOriginalWhenNotPubSub() {
    ModuleDescriptor descriptor = new ModuleDescriptor();
    descriptor.setId("mod-audit-3.1.0");
    descriptor.setProvides(new ArrayList<>());

    ModuleDescriptor result = KeycloakUtils.withPubSubResources(descriptor);

    assertSame(descriptor, result);
    assertEquals(0, descriptor.getProvides().size());
  }
}
