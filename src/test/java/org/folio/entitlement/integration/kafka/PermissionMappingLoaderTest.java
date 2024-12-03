package org.folio.entitlement.integration.kafka;

import org.folio.entitlement.integration.kafka.model.PermissionMappingValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PermissionMappingLoaderTest {

  @Test
  void testStaticMapInitialization() {
    Assertions.assertNotNull(CapabilitiesModuleEventPublisher.getValue("pubsub.events.post"));
    Assertions.assertNotNull(CapabilitiesModuleEventPublisher.getValue("audit.pub-sub-handlers.log-record-event.post"));

    PermissionMappingValue value1 = CapabilitiesModuleEventPublisher.getValue("pubsub.events.post");
    Assertions.assertEquals("/circulation/handlers/loan-related-fee-fine-closed", value1.getEndpoint());
    Assertions.assertEquals("POST", value1.getMethod());

    PermissionMappingValue value2 =
      CapabilitiesModuleEventPublisher.getValue("audit.pub-sub-handlers.log-record-event.post");
    Assertions.assertEquals("/audit/handlers/log-record", value2.getEndpoint());
    Assertions.assertEquals("POST", value2.getMethod());
  }

  @Test
  void testNonExistentKey() {
    Assertions.assertNull(CapabilitiesModuleEventPublisher.getValue("nonExistentKey"));
  }
}
