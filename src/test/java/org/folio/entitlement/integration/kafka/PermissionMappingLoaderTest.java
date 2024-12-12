package org.folio.entitlement.integration.kafka;

import org.folio.common.domain.model.ModuleDescriptor;
import org.folio.entitlement.integration.kafka.model.PermissionMappingValue;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PermissionMappingLoaderTest {

  @Test
  void testStaticMapInitialization() {
    Assertions.assertNotNull(KafkaEventUtils.getPermissionValueMapping("pubsub.events.post"));
    Assertions.assertNotNull(KafkaEventUtils.getPermissionValueMapping("audit.pub-sub-handlers.log-record-event.post"));

    Assertions.assertTrue(KafkaEventUtils.isPermissionMappingExist("pubsub.events.post"));
    Assertions.assertTrue(KafkaEventUtils.isPermissionMappingExist("audit.pub-sub-handlers.log-record-event.post"));

    PermissionMappingValue value1 = KafkaEventUtils.getPermissionValueMapping("pubsub.events.post");
    Assertions.assertEquals("/circulation/handlers/loan-related-fee-fine-closed", value1.getEndpoint());
    Assertions.assertEquals("POST", value1.getMethod());

    PermissionMappingValue value2 =
      KafkaEventUtils.getPermissionValueMapping("audit.pub-sub-handlers.log-record-event.post");
    Assertions.assertEquals("/audit/handlers/log-record", value2.getEndpoint());
    Assertions.assertEquals("POST", value2.getMethod());
  }

  @Test
  void testNonExistentKey() {
    Assertions.assertNull(KafkaEventUtils.getPermissionValueMapping("nonExistentKey"));
  }

  @Test
  void testUpdatePubSubDescriptorWithEndpoints() {
    ModuleDescriptor newDescriptor = new ModuleDescriptor();
    newDescriptor.setProvides(new ArrayList<>());
    KafkaEventUtils.addMissingResources(newDescriptor);
    Assertions.assertEquals( newDescriptor.getProvides().size(), 1);
    Assertions.assertEquals( newDescriptor.getProvides().get(0).getHandlers().size(), 3);
  }
}
