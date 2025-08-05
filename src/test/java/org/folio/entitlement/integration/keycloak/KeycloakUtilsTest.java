package org.folio.entitlement.integration.keycloak;

import java.util.ArrayList;
import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KeycloakUtilsTest {
  @Test
  void testUpdatePubSubDescriptorWithEndpoints() {
    ModuleDescriptor newDescriptor = new ModuleDescriptor();
    newDescriptor.setProvides(new ArrayList<>());
    KeycloakUtils.addPubSubResources(newDescriptor);
    Assertions.assertEquals(1, newDescriptor.getProvides().size());
    Assertions.assertEquals(12, newDescriptor.getProvides().get(0).getHandlers().size());
  }
}
