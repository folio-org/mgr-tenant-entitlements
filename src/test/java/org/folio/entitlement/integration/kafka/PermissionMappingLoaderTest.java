package org.folio.entitlement.integration.kafka;

import org.folio.entitlement.integration.kafka.model.PermissionMappingValue;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class PermissionMappingLoaderTest {

  @Test
  void testStaticMapInitialization() {
    Assertions.assertNotNull(
      KafkaEventUtils.getPermissionValueMapping("circulation.handlers.loan-related-fee-fine-closed.post"));
    Assertions.assertNotNull(KafkaEventUtils.getPermissionValueMapping("audit.pub-sub-handlers.log-record-event.post"));

    Assertions.assertTrue(
      KafkaEventUtils.isPermissionMappingExist("circulation.handlers.loan-related-fee-fine-closed.post"));
    Assertions.assertTrue(KafkaEventUtils.isPermissionMappingExist("audit.pub-sub-handlers.log-record-event.post"));

    PermissionMappingValue value1 =
      KafkaEventUtils.getPermissionValueMapping("circulation.handlers.loan-related-fee-fine-closed.post");
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
  void testGetPermissionMapping() {
    Assertions.assertEquals(12, KafkaEventUtils.getPermissionMapping().size());
  }
}
