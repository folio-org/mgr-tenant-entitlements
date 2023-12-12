package org.folio.entitlement.integration.kafka;

import static org.folio.entitlement.support.TestConstants.entitlementTopic;
import static org.mockito.Mockito.verify;

import org.folio.entitlement.integration.kafka.model.EntitlementEvent;
import org.folio.entitlement.support.TestUtils;
import org.folio.test.types.UnitTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class KafkaFolioModuleEventPublisherTest {

  @InjectMocks private EntitlementEventPublisher entitlementEventPublisher;
  @Mock private KafkaEventPublisher kafkaEventPublisher;

  @AfterEach
  void tearDown() {
    TestUtils.verifyNoMoreInteractions(this);
  }

  @Test
  void publish_positive() {
    var moduleId = "moduleId";
    var event = new EntitlementEvent();
    event.setModuleId(moduleId);

    entitlementEventPublisher.publish(event);

    verify(kafkaEventPublisher).send(entitlementTopic(), moduleId, event);
  }
}
